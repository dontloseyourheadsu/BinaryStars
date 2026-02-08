using System;

namespace BinaryStars.Application.Databases.DatabaseModels.Locations;

/// <summary>
/// Database entity representing a device location history point.
/// </summary>
public class LocationHistoryDbModel
{
    /// <summary>
    /// Gets or sets the history record identifier.
    /// </summary>
    public Guid Id { get; set; }

    /// <summary>
    /// Gets or sets the owning user identifier.
    /// </summary>
    public Guid UserId { get; set; }

    /// <summary>
    /// Gets or sets the device identifier that reported the location.
    /// </summary>
    public string DeviceId { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the recorded latitude.
    /// </summary>
    public double Latitude { get; set; }

    /// <summary>
    /// Gets or sets the recorded longitude.
    /// </summary>
    public double Longitude { get; set; }

    /// <summary>
    /// Gets or sets the optional accuracy value in meters.
    /// </summary>
    public double? AccuracyMeters { get; set; }

    /// <summary>
    /// Gets or sets the timestamp when the location was recorded.
    /// </summary>
    public DateTimeOffset RecordedAt { get; set; }
}
