using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Notes;
using System.Security.Claims;

namespace BinaryStars.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class NotesController : ControllerBase
{
    private readonly INotesReadService _notesReadService;
    private readonly INotesWriteService _notesWriteService;

    public NotesController(INotesReadService notesReadService, INotesWriteService notesWriteService)
    {
        _notesReadService = notesReadService;
        _notesWriteService = notesWriteService;
    }

    [HttpGet]
    public async Task<IActionResult> GetNotes(CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesReadService.GetNotesByUserAsync(userId, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    [HttpGet("device/{deviceId}")]
    public async Task<IActionResult> GetNotesByDevice(string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesReadService.GetNotesByUserAndDeviceAsync(userId, deviceId, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    [HttpGet("{noteId}")]
    public async Task<IActionResult> GetNoteById(Guid noteId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesReadService.GetNoteByIdAsync(noteId, userId, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }

    [HttpPost]
    public async Task<IActionResult> CreateNote([FromBody] CreateNoteRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _notesWriteService.CreateNoteAsync(userId, request, cancellationToken);

        if (result.IsSuccess)
            return CreatedAtAction(nameof(GetNoteById), new { noteId = result.Value.Id }, result.Value);

        return BadRequest(result.Errors);
    }

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

public record UpdateNoteRequestDto(string Name, string Content);
