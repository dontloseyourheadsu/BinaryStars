using System.Net.Http.Json;
using BinaryStars.Api.Models;

namespace BinaryStars.Tests.Integration.Helpers;

public static class AuthTestHelper
{
    public static async Task<string> RegisterAndLoginAsync(HttpClient client, string username, string email, string password)
    {
        var registerResponse = await client.PostAsJsonAsync("api/auth/register", new
        {
            username,
            email,
            password
        });

        registerResponse.EnsureSuccessStatusCode();

        var loginResponse = await client.PostAsJsonAsync("api/auth/login", new
        {
            email,
            password
        });

        loginResponse.EnsureSuccessStatusCode();

        var auth = await loginResponse.Content.ReadFromJsonAsync<AuthResponse>(TestJson.Options);
        if (auth == null || string.IsNullOrWhiteSpace(auth.AccessToken))
        {
            throw new InvalidOperationException("Authentication token missing from response.");
        }

        return auth.AccessToken;
    }
}
