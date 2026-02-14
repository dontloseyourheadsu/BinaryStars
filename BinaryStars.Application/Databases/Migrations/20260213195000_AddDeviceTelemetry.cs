using BinaryStars.Application.Databases.DatabaseContexts;
using Microsoft.EntityFrameworkCore.Migrations;
using Microsoft.EntityFrameworkCore.Infrastructure;

#nullable disable

namespace BinaryStars.Application.Databases.Migrations
{
    [DbContext(typeof(ApplicationDbContext))]
    [Migration("20260213195000_AddDeviceTelemetry")]
    public partial class AddDeviceTelemetry : Migration
    {
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "CpuLoadPercent",
                table: "Devices",
                type: "integer",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsAvailable",
                table: "Devices",
                type: "boolean",
                nullable: false,
                defaultValue: true);
        }

        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "CpuLoadPercent",
                table: "Devices");

            migrationBuilder.DropColumn(
                name: "IsAvailable",
                table: "Devices");
        }
    }
}
