using Grpc.Core;
using BinaryStars.Proto;

namespace BinaryStars.Proto.Services;

/// <summary>
/// gRPC service that returns simple greeting responses.
/// </summary>
public class GreeterService : Greeter.GreeterBase
{
    private readonly ILogger<GreeterService> _logger;
    /// <summary>
    /// Initializes the service with logging.
    /// </summary>
    public GreeterService(ILogger<GreeterService> logger)
    {
        _logger = logger;
    }

    /// <summary>
    /// Responds with a greeting for the provided name.
    /// </summary>
    public override Task<HelloReply> SayHello(HelloRequest request, ServerCallContext context)
    {
        return Task.FromResult(new HelloReply
        {
            Message = "Hello " + request.Name
        });
    }
}
