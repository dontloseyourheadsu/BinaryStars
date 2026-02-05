using BinaryStars.Api.Services;

namespace BinaryStars.Api.Middleware;

public sealed class TokenRefreshMiddleware
{
    private const string HeaderAccessToken = "X-Access-Token";
    private const string HeaderAccessTokenExpiresIn = "X-Access-Token-ExpiresIn";

    private readonly RequestDelegate _next;

    public TokenRefreshMiddleware(RequestDelegate next)
    {
        _next = next;
    }

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
