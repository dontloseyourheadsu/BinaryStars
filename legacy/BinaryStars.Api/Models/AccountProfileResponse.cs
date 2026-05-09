using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Api.Models;

/// <summary>
/// Response payload describing the authenticated user's profile.
/// </summary>
/// <param name="Id">The user identifier.</param>
/// <param name="Username">The user display name.</param>
/// <param name="Email">The user email address.</param>
/// <param name="Role">The assigned user role.</param>
public sealed record AccountProfileResponse(
    Guid Id,
    string Username,
    string Email,
    UserRole Role);
