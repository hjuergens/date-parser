"""Prints the official Umm al-Qura table of hijridate (1343 to 1500 AH) for the Java and Peggy modules: the Gregorian
date of 1 Muharram 1343 and one digit per month, its length minus 28 (0 to 3), twelve per year. The months follow
each other without a gap, so the first day of every month follows from the lengths. The JDK's and ICU's own tables
differ from it in some months (Shawwal 1451 has 29 days, not 30).

    uv run --project ../date-rule-lark python scripts/umm_al_qura.py

The output goes into ScheduleSemantics.java (UMM_AL_QURA_START, UMM_AL_QURA; all copies) and schedule.pegjs.
"""
from hijridate import Hijri

FIRST, LAST = 1343, 1500

months = [Hijri(year, month, 1) for year in range(FIRST, LAST + 1) for month in range(1, 13)]
for this, following in zip(months, months[1:]):
    gap = (following.to_gregorian() - this.to_gregorian()).days
    assert gap == this.month_length(), f"{this}: {gap} days to the next month"
assert all(28 <= h.month_length() <= 31 for h in months)

print(months[0].to_gregorian().isoformat())
digits = "".join(str(h.month_length() - 28) for h in months)
for i in range(0, len(digits), 96):   # eight years per line
    print(digits[i:i + 96])
