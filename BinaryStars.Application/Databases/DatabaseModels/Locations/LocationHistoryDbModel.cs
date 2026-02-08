using System;

namespace BinaryStars.Application.Databases.DatabaseModels.Locations;

public class LocationHistoryDbModel
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string DeviceId { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double? AccuracyMeters { get; set; }
    public DateTimeOffset RecordedAt { get; set; }
}
