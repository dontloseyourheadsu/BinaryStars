using BinaryStars.Tests.Integration.Fixtures;
using BinaryStars.Tests.Integration.Helpers;

namespace BinaryStars.Tests.Integration.Tests;

/// <summary>
/// Authentication tests for the password-based login flow.
/// </summary>
[Collection("integration")]
public class AuthPasswordTests
{
    private readonly IntegrationTestFixture _fixture;

    public AuthPasswordTests(IntegrationTestFixture fixture)
    {
        _fixture = fixture;
    }

    /// <summary>
    /// Registers and logs in to validate an access token is returned.
    /// </summary>
    [Fact]
    public async Task RegisterAndLogin_ReturnsAccessToken()
    {
        var client = _fixture.Factory.CreateClient();
        var suffix = Guid.NewGuid().ToString("N");
        var token = await AuthTestHelper.RegisterAndLoginAsync(
            client,
            $"user_{suffix}",
            $"user_{suffix}@example.com",
            "Password123!");

        Assert.False(string.IsNullOrWhiteSpace(token));
    }
}
