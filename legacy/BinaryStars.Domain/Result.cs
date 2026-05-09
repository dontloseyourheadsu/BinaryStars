namespace BinaryStars.Domain;

/// <summary>
/// Represents the result of an operation that returns a value.
/// </summary>
/// <typeparam name="T">The value type returned on success.</typeparam>
public class Result<T>
{
    /// <summary>
    /// Gets a value indicating whether the operation succeeded.
    /// </summary>
    public bool IsSuccess { get; }

    /// <summary>
    /// Gets the result value when the operation succeeds.
    /// </summary>
    public T Value { get; }

    /// <summary>
    /// Gets the error messages when the operation fails.
    /// </summary>
    public List<string> Errors { get; }

    private Result(bool isSuccess, T value, List<string>? errors)
    {
        IsSuccess = isSuccess;
        Value = value;
        Errors = errors ?? new List<string>();
    }

    /// <summary>
    /// Creates a successful result with the provided value.
    /// </summary>
    /// <param name="value">The value to return.</param>
    /// <returns>A successful result.</returns>
    public static Result<T> Success(T value) => new(true, value, null);

    /// <summary>
    /// Creates a failed result with one or more error messages.
    /// </summary>
    /// <param name="errors">The error messages describing the failure.</param>
    /// <returns>A failed result.</returns>
    public static Result<T> Failure(List<string> errors) => new(false, default!, errors);

    /// <summary>
    /// Creates a failed result with a single error message.
    /// </summary>
    /// <param name="error">The error message describing the failure.</param>
    /// <returns>A failed result.</returns>
    public static Result<T> Failure(string error) => new(false, default!, new List<string> { error });
}

/// <summary>
/// Represents the result of an operation that does not return a value.
/// </summary>
public class Result
{
    /// <summary>
    /// Gets a value indicating whether the operation succeeded.
    /// </summary>
    public bool IsSuccess { get; }

    /// <summary>
    /// Gets the error messages when the operation fails.
    /// </summary>
    public List<string> Errors { get; }

    private Result(bool isSuccess, List<string>? errors)
    {
        IsSuccess = isSuccess;
        Errors = errors ?? new List<string>();
    }

    /// <summary>
    /// Creates a successful result without a value.
    /// </summary>
    /// <returns>A successful result.</returns>
    public static Result Success() => new(true, null);

    /// <summary>
    /// Creates a failed result with one or more error messages.
    /// </summary>
    /// <param name="errors">The error messages describing the failure.</param>
    /// <returns>A failed result.</returns>
    public static Result Failure(List<string> errors) => new(false, errors);

    /// <summary>
    /// Creates a failed result with a single error message.
    /// </summary>
    /// <param name="error">The error message describing the failure.</param>
    /// <returns>A failed result.</returns>
    public static Result Failure(string error) => new(false, new List<string> { error });
}
