using System.Text.Json;

namespace BinaryStars.Tests.Integration.Helpers;

/// <summary>
/// JSON serializer configuration for integration tests.
/// </summary>
public static class TestJson
{
    /// <summary>
    /// Default JSON options used by tests.
    /// </summary>
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true
    };
}
