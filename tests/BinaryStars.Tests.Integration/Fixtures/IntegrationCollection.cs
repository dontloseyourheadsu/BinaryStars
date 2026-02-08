namespace BinaryStars.Tests.Integration.Fixtures;

/// <summary>
/// XUnit collection definition for integration test fixture reuse.
/// </summary>
[CollectionDefinition("integration")]
public class IntegrationCollection : ICollectionFixture<IntegrationTestFixture>
{
}
