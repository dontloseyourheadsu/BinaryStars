using Microsoft.Extensions.Logging;
using System.Security.Claims;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Services.Notifications;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides notification send/schedule/sync endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
[Authorize]
public class NotificationsController : ControllerBase
{
    private readonly ILogger<NotificationsController> _logger;

    private readonly INotificationsReadService _notificationsReadService;
    private readonly INotificationsWriteService _notificationsWriteService;
    private readonly IDeviceRepository _deviceRepository;
    private readonly MessagingKafkaService _kafkaService;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotificationsController"/> class.
    /// </summary>
    public NotificationsController(
        INotificationsReadService notificationsReadService,
        INotificationsWriteService notificationsWriteService,
        IDeviceRepository deviceRepository,
        MessagingKafkaService kafkaService, ILogger<NotificationsController> logger)
    {
        _logger = logger;

        _notificationsReadService = notificationsReadService;
        _notificationsWriteService = notificationsWriteService;
        _deviceRepository = deviceRepository;
        _kafkaService = kafkaService;
    }

    /// <summary>
    /// Sends an immediate notification to a target device.
    /// </summary>
    /// <param name="request">The send request payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpPost("send")]
    public async Task<IActionResult> Send([FromBody] SendNotificationRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        if (string.IsNullOrWhiteSpace(request.Title) || string.IsNullOrWhiteSpace(request.Body))
            return BadRequest(new[] { "Title and body are required." });

        var sender = await _deviceRepository.GetByIdAsync(request.SenderDeviceId, cancellationToken);
        var target = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (sender == null || target == null || sender.UserId != userId || target.UserId != userId)
            return BadRequest(new[] { "Invalid device." });

        var message = new DeviceNotificationMessage(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            request.Title.Trim(),
            request.Body.Trim(),
            DateTimeOffset.UtcNow);

        const KafkaAuthMode authMode = KafkaAuthMode.Scram;
        await _kafkaService.PublishNotificationAsync(message, authMode, null, cancellationToken);

        var pendingResult = await _notificationsWriteService.SetPendingSyncFlagAsync(userId, request.TargetDeviceId, true, cancellationToken);
        if (!pendingResult.IsSuccess)
            return BadRequest(pendingResult.Errors);

        return Ok(message);
    }

    /// <summary>
    /// Gets all notification schedules targeting the specified device.
    /// </summary>
    /// <param name="deviceId">The target device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpGet("schedules")]
    public async Task<IActionResult> GetSchedules([FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notificationsReadService.GetSchedulesAsync(userId, deviceId, cancellationToken);
        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Creates a notification schedule.
    /// </summary>
    /// <param name="request">The create request payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpPost("schedules")]
    public async Task<IActionResult> CreateSchedule([FromBody] CreateNotificationScheduleRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notificationsWriteService.CreateScheduleAsync(
            userId,
            new CreateNotificationScheduleRequest(
                request.SourceDeviceId,
                request.TargetDeviceId,
                request.Title,
                request.Body,
                request.IsEnabled,
                request.ScheduledForUtc,
                request.RepeatMinutes),
            cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Updates a notification schedule.
    /// </summary>
    /// <param name="scheduleId">The schedule identifier.</param>
    /// <param name="request">The update request payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpPut("schedules/{scheduleId:guid}")]
    public async Task<IActionResult> UpdateSchedule(Guid scheduleId, [FromBody] UpdateNotificationScheduleRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notificationsWriteService.UpdateScheduleAsync(
            userId,
            new UpdateNotificationScheduleRequest(
                scheduleId,
                request.SourceDeviceId,
                request.TargetDeviceId,
                request.Title,
                request.Body,
                request.IsEnabled,
                request.ScheduledForUtc,
                request.RepeatMinutes),
            cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Deletes a notification schedule.
    /// </summary>
    /// <param name="scheduleId">The schedule identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpDelete("schedules/{scheduleId:guid}")]
    public async Task<IActionResult> DeleteSchedule(Guid scheduleId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notificationsWriteService.DeleteScheduleAsync(userId, scheduleId, cancellationToken);

        if (result.IsSuccess)
            return Ok();

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Pulls pending notification payloads and schedule settings for a device.
    /// </summary>
    /// <param name="deviceId">The target device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpGet("pull")]
    public async Task<IActionResult> Pull([FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var pendingFlagResult = await _notificationsReadService.GetPendingSyncFlagAsync(userId, deviceId, cancellationToken);
        if (!pendingFlagResult.IsSuccess)
            return BadRequest(pendingFlagResult.Errors);

        var schedulesResult = await _notificationsReadService.GetSchedulesAsync(userId, deviceId, cancellationToken);
        if (!schedulesResult.IsSuccess)
            return BadRequest(schedulesResult.Errors);

        const KafkaAuthMode authMode = KafkaAuthMode.Scram;
        var notifications = await _kafkaService.ConsumePendingNotificationsAsync(deviceId, userId, authMode, null, cancellationToken);
        foreach (var notification in notifications)
        {
            await _kafkaService.DeleteNotificationAsync(notification.Id, authMode, null, cancellationToken);
        }

        var response = new PullNotificationsResponseDto(
            pendingFlagResult.Value || notifications.Count > 0,
            notifications,
            schedulesResult.Value);

        return Ok(response);
    }

    /// <summary>
    /// Acknowledges that notification sync payloads were applied on device.
    /// </summary>
    /// <param name="request">The acknowledgement request payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpPost("ack")]
    public async Task<IActionResult> Acknowledge([FromBody] NotificationSyncAckRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notificationsWriteService.SetPendingSyncFlagAsync(userId, request.DeviceId, false, cancellationToken);

        if (result.IsSuccess)
            return Ok();

        return BadRequest(result.Errors);
    }

}

/// <summary>
/// Request payload for immediate notification send.
/// </summary>
public record SendNotificationRequestDto(string SenderDeviceId, string TargetDeviceId, string Title, string Body);

/// <summary>
/// Request payload for notification schedule create.
/// </summary>
public record CreateNotificationScheduleRequestDto(
    string SourceDeviceId,
    string TargetDeviceId,
    string Title,
    string Body,
    bool IsEnabled,
    DateTimeOffset? ScheduledForUtc,
    int? RepeatMinutes);

/// <summary>
/// Request payload for notification schedule update.
/// </summary>
public record UpdateNotificationScheduleRequestDto(
    string SourceDeviceId,
    string TargetDeviceId,
    string Title,
    string Body,
    bool IsEnabled,
    DateTimeOffset? ScheduledForUtc,
    int? RepeatMinutes);

/// <summary>
/// Pull response for notifications and schedule settings.
/// </summary>
public record PullNotificationsResponseDto(
    bool HasPendingNotificationSync,
    List<DeviceNotificationMessage> Notifications,
    List<NotificationScheduleResponse> Schedules);

/// <summary>
/// Request payload for notification sync acknowledgement.
/// </summary>
public record NotificationSyncAckRequestDto(string DeviceId);
