using System.Text.Json;

namespace BinaryStars.Tests.Integration.Helpers;

public static class TestJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true
    };
}
