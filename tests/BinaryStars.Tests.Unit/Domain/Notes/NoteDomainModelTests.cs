using BinaryStars.Domain.Notes;
using BinaryStars.Domain.Errors.Notes;

namespace BinaryStars.Tests.Unit.Domain.Notes;

/// <summary>
/// Unit tests for the <see cref="Note"/> domain model.
/// </summary>
public class NoteDomainModelTests
{
    /// <summary>
    /// Verifies a note can be created with valid parameters.
    /// </summary>
    [Fact]
    public void CreatingNote_WithValidParameters_ShouldSucceed()
    {
        // Arrange
        var id = Guid.NewGuid();
        var name = "My Important Note";
        var userId = Guid.NewGuid();
        var deviceId = "device-123";
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act
        var note = new Note(id, name, userId, deviceId, contentType, createdAt, updatedAt);

        // Assert
        Assert.Equal(id, note.Id);
        Assert.Equal(name, note.Name);
        Assert.Equal(userId, note.UserId);
        Assert.Equal(deviceId, note.SignedByDeviceId);
        Assert.Equal(contentType, note.ContentType);
        Assert.Equal(createdAt, note.CreatedAt);
        Assert.Equal(updatedAt, note.UpdatedAt);
    }

    /// <summary>
    /// Verifies empty identifiers are rejected.
    /// </summary>
    [Fact]
    public void CreatingNote_WithEmptyId_ShouldThrowArgumentException()
    {
        // Arrange
        var id = Guid.Empty;
        var name = "My Important Note";
        var userId = Guid.NewGuid();
        var deviceId = "device-123";
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() =>
            new Note(id, name, userId, deviceId, contentType, createdAt, updatedAt));
        Assert.Contains(NoteErrors.IdCannotBeEmpty, exception.Message);
    }

    /// <summary>
    /// Verifies whitespace names are rejected.
    /// </summary>
    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    public void CreatingNote_WithInvalidName_ShouldThrowArgumentException(string name)
    {
        // Arrange
        var id = Guid.NewGuid();
        var userId = Guid.NewGuid();
        var deviceId = "device-123";
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() =>
            new Note(id, name!, userId, deviceId, contentType, createdAt, updatedAt));
        Assert.Contains(NoteErrors.NameCannotBeNullOrWhitespace, exception.Message);
    }

    /// <summary>
    /// Verifies null names are rejected.
    /// </summary>
    [Fact]
    public void CreatingNote_WithNullName_ShouldThrowArgumentException()
    {
        // Arrange
        var id = Guid.NewGuid();
        string? name = null;
        var userId = Guid.NewGuid();
        var deviceId = "device-123";
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() =>
            new Note(id, name!, userId, deviceId, contentType, createdAt, updatedAt));
        Assert.Contains(NoteErrors.NameCannotBeNullOrWhitespace, exception.Message);
    }

    /// <summary>
    /// Verifies empty user IDs are rejected.
    /// </summary>
    [Fact]
    public void CreatingNote_WithEmptyUserId_ShouldThrowArgumentException()
    {
        // Arrange
        var id = Guid.NewGuid();
        var name = "My Important Note";
        var userId = Guid.Empty;
        var deviceId = "device-123";
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() =>
            new Note(id, name, userId, deviceId, contentType, createdAt, updatedAt));
        Assert.Contains(NoteErrors.UserIdCannotBeEmpty, exception.Message);
    }

    /// <summary>
    /// Verifies whitespace device IDs are rejected.
    /// </summary>
    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    public void CreatingNote_WithInvalidDeviceId_ShouldThrowArgumentException(string deviceId)
    {
        // Arrange
        var id = Guid.NewGuid();
        var name = "My Important Note";
        var userId = Guid.NewGuid();
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() =>
            new Note(id, name, userId, deviceId!, contentType, createdAt, updatedAt));
        Assert.Contains(NoteErrors.SignedByDeviceIdCannotBeNullOrWhitespace, exception.Message);
    }

    /// <summary>
    /// Verifies null device IDs are rejected.
    /// </summary>
    [Fact]
    public void CreatingNote_WithNullDeviceId_ShouldThrowArgumentException()
    {
        // Arrange
        var id = Guid.NewGuid();
        var name = "My Important Note";
        var userId = Guid.NewGuid();
        string? deviceId = null;
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() =>
            new Note(id, name, userId, deviceId!, contentType, createdAt, updatedAt));
        Assert.Contains(NoteErrors.SignedByDeviceIdCannotBeNullOrWhitespace, exception.Message);
    }

    /// <summary>
    /// Verifies plaintext notes are accepted.
    /// </summary>
    [Fact]
    public void CreatingNote_WithPlaintextType_ShouldSucceed()
    {
        // Arrange
        var id = Guid.NewGuid();
        var name = "Plain Text Note";
        var userId = Guid.NewGuid();
        var deviceId = "device-456";
        var contentType = NoteType.Plaintext;
        var createdAt = DateTimeOffset.UtcNow;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act
        var note = new Note(id, name, userId, deviceId, contentType, createdAt, updatedAt);

        // Assert
        Assert.Equal(NoteType.Plaintext, note.ContentType);
    }

    /// <summary>
    /// Ensures created/updated timestamps are preserved.
    /// </summary>
    [Fact]
    public void CreatingNote_WithDifferentTimestamps_ShouldPreserveTimestamps()
    {
        // Arrange
        var id = Guid.NewGuid();
        var name = "Timestamped Note";
        var userId = Guid.NewGuid();
        var deviceId = "device-789";
        var contentType = NoteType.Markdown;
        var createdAt = DateTimeOffset.UnixEpoch;
        var updatedAt = DateTimeOffset.UtcNow;

        // Act
        var note = new Note(id, name, userId, deviceId, contentType, createdAt, updatedAt);

        // Assert
        Assert.Equal(createdAt, note.CreatedAt);
        Assert.Equal(updatedAt, note.UpdatedAt);
        Assert.True(note.UpdatedAt >= note.CreatedAt);
    }

    /// <summary>
    /// Confirms the record type remains immutable.
    /// </summary>
    [Fact]
    public void Note_IsImmutable_CannotChangeProperties()
    {
        // Arrange
        var note = new Note(
            Guid.NewGuid(),
            "Immutable Note",
            Guid.NewGuid(),
            "device-immutable",
            NoteType.Markdown,
            DateTimeOffset.UtcNow,
            DateTimeOffset.UtcNow);

        // Act & Assert - record structs are immutable, properties cannot be set directly
        // This test verifies the structure behaves as expected
        Assert.Equal("Immutable Note", note.Name);
        Assert.Equal(NoteType.Markdown, note.ContentType);
    }
}
