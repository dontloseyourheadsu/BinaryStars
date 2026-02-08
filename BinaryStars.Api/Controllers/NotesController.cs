using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Notes;
using System.Security.Claims;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides note management endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
[Authorize]
public class NotesController : ControllerBase
{
    private readonly INotesReadService _notesReadService;
    private readonly INotesWriteService _notesWriteService;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotesController"/> class.
    /// </summary>
    /// <param name="notesReadService">The note read service.</param>
    /// <param name="notesWriteService">The note write service.</param>
    public NotesController(INotesReadService notesReadService, INotesWriteService notesWriteService)
    {
        _notesReadService = notesReadService;
        _notesWriteService = notesWriteService;
    }

    /// <summary>
    /// Gets all notes for the authenticated user.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of notes.</returns>
    [HttpGet]
    public async Task<IActionResult> GetNotes(CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesReadService.GetNotesByUserAsync(userId, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Gets notes for a specific device owned by the user.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of notes.</returns>
    [HttpGet("device/{deviceId}")]
    public async Task<IActionResult> GetNotesByDevice(string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesReadService.GetNotesByUserAndDeviceAsync(userId, deviceId, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Gets a specific note by identifier.
    /// </summary>
    /// <param name="noteId">The note identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The note response.</returns>
    [HttpGet("{noteId}")]
    public async Task<IActionResult> GetNoteById(Guid noteId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesReadService.GetNoteByIdAsync(noteId, userId, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Creates a new note for the authenticated user.
    /// </summary>
    /// <param name="request">The note creation request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The created note.</returns>
    [HttpPost]
    public async Task<IActionResult> CreateNote([FromBody] CreateNoteRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesWriteService.CreateNoteAsync(userId, request, cancellationToken);

        if (result.IsSuccess)
            return CreatedAtAction(nameof(GetNoteById), new { noteId = result.Value.Id }, result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Updates an existing note for the authenticated user.
    /// </summary>
    /// <param name="noteId">The note identifier.</param>
    /// <param name="request">The update request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The updated note.</returns>
    [HttpPut("{noteId}")]
    public async Task<IActionResult> UpdateNote(Guid noteId, [FromBody] UpdateNoteRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var updateRequest = new UpdateNoteRequest(noteId, request.Name, request.Content);
        var result = await _notesWriteService.UpdateNoteAsync(userId, updateRequest, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Deletes a note owned by the authenticated user.
    /// </summary>
    /// <param name="noteId">The note identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Ok on success.</returns>
    [HttpDelete("{noteId}")]
    public async Task<IActionResult> DeleteNote(Guid noteId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesWriteService.DeleteNoteAsync(noteId, userId, cancellationToken);

        if (result.IsSuccess)
            return Ok();

        return BadRequest(result.Errors);
    }
}

/// <summary>
/// Request payload for updating a note.
/// </summary>
/// <param name="Name">The updated note title.</param>
/// <param name="Content">The updated note content.</param>
public record UpdateNoteRequestDto(string Name, string Content);
