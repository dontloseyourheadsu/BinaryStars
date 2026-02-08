using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BinaryStars.Application.Databases.Migrations
{
    /// <inheritdoc />
    public partial class AddLocationHistory : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "LocationHistory",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    UserId = table.Column<Guid>(type: "uuid", nullable: false),
                    DeviceId = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    Latitude = table.Column<double>(type: "double precision", nullable: false),
                    Longitude = table.Column<double>(type: "double precision", nullable: false),
                    AccuracyMeters = table.Column<double>(type: "double precision", nullable: true),
                    RecordedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_LocationHistory", x => x.Id);
                    table.ForeignKey(
                        name: "FK_LocationHistory_AspNetUsers_UserId",
                        column: x => x.UserId,
                        principalTable: "AspNetUsers",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_LocationHistory_DeviceId",
                table: "LocationHistory",
                column: "DeviceId");

            migrationBuilder.CreateIndex(
                name: "IX_LocationHistory_UserId",
                table: "LocationHistory",
                column: "UserId");

            migrationBuilder.CreateIndex(
                name: "IX_LocationHistory_UserId_DeviceId_RecordedAt",
                table: "LocationHistory",
                columns: new[] { "UserId", "DeviceId", "RecordedAt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "LocationHistory");
        }
    }
}
