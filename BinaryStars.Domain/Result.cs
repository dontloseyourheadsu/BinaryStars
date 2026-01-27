namespace BinaryStars.Domain;

public class Result<T>
{
    public bool IsSuccess { get; }
    public T Value { get; }
    public List<string> Errors { get; }

    private Result(bool isSuccess, T value, List<string>? errors)
    {
        IsSuccess = isSuccess;
        Value = value;
        Errors = errors ?? new List<string>();
    }

    public static Result<T> Success(T value) => new(true, value, null);

    // Using default! to suppress the nullable warning for T when failure, as we don't access Value on failure.
    public static Result<T> Failure(List<string> errors) => new(false, default!, errors);
    public static Result<T> Failure(string error) => new(false, default!, new List<string> { error });
}

public class Result
{
    public bool IsSuccess { get; }
    public List<string> Errors { get; }

    private Result(bool isSuccess, List<string>? errors)
    {
        IsSuccess = isSuccess;
        Errors = errors ?? new List<string>();
    }

    public static Result Success() => new(true, null);
    public static Result Failure(List<string> errors) => new(false, errors);
    public static Result Failure(string error) => new(false, new List<string> { error });
}
