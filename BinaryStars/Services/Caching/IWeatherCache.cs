namespace BinaryStars.Services.Caching;
using WeatherForecast = BinaryStars.DataContracts.WeatherForecast;
public interface IWeatherCache
{
    ValueTask<IImmutableList<WeatherForecast>> GetForecast(CancellationToken token);
}
