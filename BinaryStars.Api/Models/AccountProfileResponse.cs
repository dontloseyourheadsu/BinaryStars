using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Api.Models;

public sealed record AccountProfileResponse(
    Guid Id,
    string Username,
    string Email,
    UserRole Role);
