use std::time::{SystemTime, UNIX_EPOCH};

#[test]
#[ignore = "manual integration test: requires an active desktop user session"]
fn trigger_notification_via_real_app_function() {
    let desktop = std::env::var("XDG_CURRENT_DESKTOP")
        .unwrap_or_default()
        .to_ascii_lowercase();

    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("clock should be monotonic")
        .as_secs();

    let title = format!("BinaryStars Linux integration {}", now);
    let body = "This notification was triggered by BinaryStars integration test";

    let backend = binarystarslinux_lib::trigger_linux_notification(&title, body)
        .expect("real app notification function should trigger successfully");

    if desktop.contains("gnome") {
        assert_eq!(
            backend, "gdbus",
            "GNOME should use gdbus first for desktop notification"
        );
    } else if desktop.contains("kde") || desktop.contains("plasma") {
        assert!(
            backend == "qdbus" || backend == "qdbus6",
            "KDE/Plasma should use qdbus/qdbus6 first for desktop notification"
        );
    }
}
