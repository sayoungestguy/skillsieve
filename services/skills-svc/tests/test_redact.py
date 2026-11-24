from app.extraction.redact import redact


def test_should_remove_emails_phones_and_tracking_urls() -> None:
    text = (
        "Send CV to jane.tan+jobs@example.com.sg or call +65 9123 4567 / 8123-4567. "
        "Intl: +44 (20) 7946 0958. Apply: https://jobs.example.com/apply?ref=abc&name=jane "
        "Docs at https://example.com/team stay."
    )

    out = redact(text)

    assert "jane" not in out
    assert "9123" not in out
    assert "8123" not in out
    assert "7946" not in out
    assert "ref=abc" not in out
    assert out.count("[email]") == 1
    assert out.count("[phone]") == 3
    assert "https://example.com/team" in out


def test_should_keep_numbers_that_are_not_phones() -> None:
    assert redact("3+ years, Java 21, 2 OCPU, 12 GB, salary 7000-9000") == (
        "3+ years, Java 21, 2 OCPU, 12 GB, salary 7000-9000"
    )
