using System.Net.Http.Headers;

namespace BinaryStars.Tests.Integration.Helpers;

/// <summary>
/// Extension helpers for integration test HTTP clients.
/// </summary>
public static class HttpClientExtensions
{
    /// <summary>
    /// Sets the Authorization header to a bearer token.
    /// </summary>
    public static void SetBearerToken(this HttpClient client, string token)
    {
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
    }
}
