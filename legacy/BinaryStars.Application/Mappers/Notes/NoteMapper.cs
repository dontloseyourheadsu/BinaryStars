using BinaryStars.Application.Databases.DatabaseModels.Notes;
using BinaryStars.Domain.Notes;

namespace BinaryStars.Application.Mappers.Notes;

/// <summary>
/// Maps note database models to domain models and back.
/// </summary>
public static class NoteMapper
{
    /// <summary>
    /// Converts a database note model into the domain note model.
    /// </summary>
    /// <param name="model">The database model.</param>
    /// <returns>The domain model.</returns>
    public static Note ToDomain(this NoteDbModel model)
    {
        return new Note(
            model.Id,
            model.Name,
            model.UserId,
            model.SignedByDeviceId,
            model.ContentType,
            model.CreatedAt,
            model.UpdatedAt
        );
    }

    /// <summary>
    /// Converts a domain note model into the database note model.
    /// </summary>
    /// <param name="domain">The domain model.</param>
    /// <returns>The database model.</returns>
    public static NoteDbModel ToDb(this Note domain)
    {
        return new NoteDbModel
        {
            Id = domain.Id,
            Name = domain.Name,
            UserId = domain.UserId,
            SignedByDeviceId = domain.SignedByDeviceId,
            ContentType = domain.ContentType,
            CreatedAt = domain.CreatedAt,
            UpdatedAt = domain.UpdatedAt,
            Content = "{}" // Content is managed separately via service
        };
    }
}
