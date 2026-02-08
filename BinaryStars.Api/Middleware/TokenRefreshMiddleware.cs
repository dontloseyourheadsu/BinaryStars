using BinaryStars.Api.Services;

namespace BinaryStars.Api.Middleware;

/// <summary>
/// Adds a refreshed access token to authenticated responses.
/// </summary>
public sealed class TokenRefreshMiddleware
{
    private const string HeaderAccessToken = "X-Access-Token";
    private const string HeaderAccessTokenExpiresIn = "X-Access-Token-ExpiresIn";

    private readonly RequestDelegate _next;

    /// <summary>
    /// Initializes a new instance of the <see cref="TokenRefreshMiddleware"/> class.
    /// </summary>
    /// <param name="next">The next middleware in the pipeline.</param>
    public TokenRefreshMiddleware(RequestDelegate next)
    {
        _next = next;
    }

    /// <summary>
    /// Invokes the middleware and injects refreshed token headers.
    /// </summary>
    /// <param name="context">The HTTP context.</param>
    /// <param name="tokenService">The JWT token service.</param>
    public async Task InvokeAsync(HttpContext context, JwtTokenService tokenService)
    {
        if (context.User?.Identity?.IsAuthenticated == true)
        {
            context.Response.OnStarting(() =>
            {
                var refreshed = tokenService.CreateTokenFromClaims(context.User);
                context.Response.Headers[HeaderAccessToken] = refreshed.AccessToken;
                context.Response.Headers[HeaderAccessTokenExpiresIn] = refreshed.ExpiresIn.ToString();
                return Task.CompletedTask;
            });
        }

        await _next(context);
    }
}
