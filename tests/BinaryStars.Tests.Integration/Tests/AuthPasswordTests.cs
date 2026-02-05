using BinaryStars.Tests.Integration.Fixtures;
using BinaryStars.Tests.Integration.Helpers;

namespace BinaryStars.Tests.Integration.Tests;

[Collection("integration")]
public class AuthPasswordTests
{
    private readonly IntegrationTestFixture _fixture;

    public AuthPasswordTests(IntegrationTestFixture fixture)
    {
        _fixture = fixture;
    }

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
