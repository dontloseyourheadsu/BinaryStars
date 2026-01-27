using BinaryStars.Domain.Accounts.Users;
using BinaryStars.Domain.Errors.Accounts.Users;

namespace BinaryStars.Tests.Unit.Domain.Accounts.Users;

public class UserDomainModelTests
{
    [Fact]
    public void CreatingUser_WithValidParameters_ShouldSucceed()
    {
        // Arrange
        var id = Guid.NewGuid();
        var username = "testuser";
        var email = "testuser@example.com";
        var role = UserRole.Free;

        // Act
        var user = new User(id, username, email, role);

        // Assert
        Assert.Equal(id, user.Id);
        Assert.Equal(username, user.Username);
        Assert.Equal(email, user.Email);
        Assert.Equal(role, user.Role);
    }

    [Theory]
    [InlineData("00000000-0000-0000-0000-000000000000", "testuser", "testuser@example.com", UserRole.Free, UserErrors.IdCannotBeEmpty)]
    [InlineData("d290f1ee-6c54-4b01-90e6-d701748f0851", null, "testuser@example.com", UserRole.Free, UserErrors.UsernameCannotBeNullOrWhitespace)]
    [InlineData("d290f1ee-6c54-4b01-90e6-d701748f0851", "   ", "testuser@example.com", UserRole.Free, UserErrors.UsernameCannotBeNullOrWhitespace)]
    [InlineData("d290f1ee-6c54-4b01-90e6-d701748f0851", "testuser", null, UserRole.Free, UserErrors.EmailCannotBeNullOrWhitespace)]
    [InlineData("d290f1ee-6c54-4b01-90e6-d701748f0851", "testuser", "   ", UserRole.Free, UserErrors.EmailCannotBeNullOrWhitespace)]
    public void CreatingUser_WithInvalidParameters_ShouldThrowArgumentException(
        string idString,
        string? username,
        string? email,
        UserRole role,
        string expectedErrorMessage)
    {
        // Act & Assert
        var id = Guid.Parse(idString);
        var exception = Assert.Throws<ArgumentException>(() => new User(id, username!, email!, role));
        Assert.Contains(expectedErrorMessage, exception.Message);
    }
}