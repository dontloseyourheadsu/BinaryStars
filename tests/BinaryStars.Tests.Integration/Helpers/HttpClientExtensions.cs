using System.Net.Http.Headers;

namespace BinaryStars.Tests.Integration.Helpers;

public static class HttpClientExtensions
{
    public static void SetBearerToken(this HttpClient client, string token)
    {
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
    }
}
