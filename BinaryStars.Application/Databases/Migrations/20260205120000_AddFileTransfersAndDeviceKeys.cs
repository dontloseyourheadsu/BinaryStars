using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BinaryStars.Application.Databases.Migrations
{
    /// <inheritdoc />
    public partial class AddFileTransfersAndDeviceKeys : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "PublicKey",
                table: "Devices",
                type: "character varying(4096)",
                maxLength: 4096,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "PublicKeyAlgorithm",
                table: "Devices",
                type: "character varying(100)",
                maxLength: 100,
                nullable: true);

            migrationBuilder.AddColumn<DateTimeOffset>(
                name: "PublicKeyCreatedAt",
                table: "Devices",
                type: "timestamp with time zone",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "FileTransfers",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    FileName = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: false),
                    ContentType = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    SizeBytes = table.Column<long>(type: "bigint", nullable: false),
                    SenderUserId = table.Column<Guid>(type: "uuid", nullable: false),
                    TargetUserId = table.Column<Guid>(type: "uuid", nullable: false),
                    SenderDeviceId = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    TargetDeviceId = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    Status = table.Column<int>(type: "integer", nullable: false),
                    FailureReason = table.Column<string>(type: "text", nullable: true),
                    EncryptionEnvelope = table.Column<string>(type: "jsonb", nullable: true),
                    ChunkSizeBytes = table.Column<int>(type: "integer", nullable: false),
                    PacketCount = table.Column<int>(type: "integer", nullable: false),
                    KafkaTopic = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    KafkaPartition = table.Column<int>(type: "integer", nullable: true),
                    KafkaStartOffset = table.Column<long>(type: "bigint", nullable: true),
                    KafkaEndOffset = table.Column<long>(type: "bigint", nullable: true),
                    KafkaAuthMode = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    ExpiresAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    CompletedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_FileTransfers", x => x.Id);
                });

            migrationBuilder.CreateIndex(
                name: "IX_FileTransfers_ExpiresAt",
                table: "FileTransfers",
                column: "ExpiresAt");

            migrationBuilder.CreateIndex(
                name: "IX_FileTransfers_SenderUserId",
                table: "FileTransfers",
                column: "SenderUserId");

            migrationBuilder.CreateIndex(
                name: "IX_FileTransfers_Status",
                table: "FileTransfers",
                column: "Status");

            migrationBuilder.CreateIndex(
                name: "IX_FileTransfers_TargetDeviceId",
                table: "FileTransfers",
                column: "TargetDeviceId");

            migrationBuilder.CreateIndex(
                name: "IX_FileTransfers_TargetUserId",
                table: "FileTransfers",
                column: "TargetUserId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "FileTransfers");

            migrationBuilder.DropColumn(
                name: "PublicKey",
                table: "Devices");

            migrationBuilder.DropColumn(
                name: "PublicKeyAlgorithm",
                table: "Devices");

            migrationBuilder.DropColumn(
                name: "PublicKeyCreatedAt",
                table: "Devices");
        }
    }
}
