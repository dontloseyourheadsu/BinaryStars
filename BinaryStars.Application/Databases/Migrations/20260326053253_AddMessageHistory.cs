using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BinaryStars.Application.Databases.Migrations
{
    /// <inheritdoc />
    public partial class AddMessageHistory : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "MessageHistory",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    SenderDeviceId = table.Column<string>(type: "text", nullable: false),
                    TargetDeviceId = table.Column<string>(type: "text", nullable: false),
                    Body = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: false),
                    SentAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_MessageHistory", x => x.Id);
                    table.ForeignKey(
                        name: "FK_MessageHistory_AspNetUsers_UserId",
                        column: x => x.UserId,
                        principalTable: "AspNetUsers",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_MessageHistory_UserId_SenderDeviceId_TargetDeviceId_SentAt",
                table: "MessageHistory",
                columns: new[] { "UserId", "SenderDeviceId", "TargetDeviceId", "SentAt" });

            migrationBuilder.CreateIndex(
                name: "IX_MessageHistory_UserId_SentAt",
                table: "MessageHistory",
                columns: new[] { "UserId", "SentAt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "MessageHistory");
        }
    }
}
