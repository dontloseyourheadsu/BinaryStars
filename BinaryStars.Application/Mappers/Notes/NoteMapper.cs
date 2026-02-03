using BinaryStars.Application.Databases.DatabaseModels.Notes;
using BinaryStars.Domain.Notes;

namespace BinaryStars.Application.Mappers.Notes;

public static class NoteMapper
{
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
