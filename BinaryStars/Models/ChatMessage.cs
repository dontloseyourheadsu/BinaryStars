using System;
using System.Text.Json.Serialization;

namespace BinaryStars.Models;

public record ChatMessage(
    string Sender, 
    string Text, 
    DateTimeOffset Timestamp, 
    bool IsMe, 
    FileAttachment? Attachment = null);

public record FileAttachment(
    string Name, 
    long Size, 
    string Type, 
    string? LocalPath = null, 
    bool IsDownloading = false, 
    double Progress = 0,
    bool IsImage = false,
    [property: JsonIgnore] byte[]? Data = null);
