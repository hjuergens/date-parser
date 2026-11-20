# Quick Reference Guide: date-parser Project

> **Purpose**: Fast lookup for implementation selection and project navigation  
> **Full Documentation**: See [TAXONOMY.md](./TAXONOMY.md) for complete details  
> **Version**: 1.0  
> **Last Updated**: 2026-11-20

---

## 🎯 Implementation Selector

### Quick Decision Tree

```
What language are you using?
├── Python → Use LARK
├── Java → Use ANTLR-JAVA
├── Scala → Use COMBINATORS
├── C → Use FLEX-BISON
├── Clojure → Use INSTAPARSE
└── JavaScript → Use PEGGY
```

---

## 📋 Implementation Comparison

### Production Implementations (Recommended)

| Implementation | Language | Algorithm | Features | Primary Use | Maturity |
|---------------|----------|-----------|----------|-------------|----------|
| **`date-rule-lark`** | Python | Earley/LALR(1) | Full+ | **Holidays-Events** | Production |
| **`date-rule-antlr-java`** | Java | ALL(*) | Full | General/Reference | Production |
| **`date-rule-antlr-python`** | Python | ALL(*) | Full | Python Ecosystem | Production |
| **`date-rule-combinators`** | Scala | Recursive Descent | Full | Functional | Production |
| **`date-rule-flex-bison`** | C | LALR(1) | Full | C Integration | Production |
| **`date-rule-sablecc`** | Java | LALR(1) | Full | Java Alternative | Production |

### Secondary Implementations

| Implementation | Language | Algorithm | Features | Notes |
|---------------|----------|-----------|----------|-------|
| `date-rule-bison` | Java | LALR(1) | Near-Full | GNU Bison with Java skeleton |
| `date-rule-cup-jflex` | Java | LALR(1) | Near-Full | CUP + JFlex |
| `date-rule-javacc` | Java | LL(k) | Near-Full | JavaCC |
| `date-rule-coco` | Java | LL(1) | Near-Full | Coco/R |

---

## 🎯 Use Case Mapping

### By Application Domain

| Use Case | Best Choice | Alternatives | Notes |
|----------|-------------|--------------|-------|
| **Holidays-Events Rules** | **Lark** | ANTLR-Java, ANTLR-Python | 229 rules, 100% validated 2021-2031 |
| Financial Date Calculations | Lark, ANTLR-Java | Combinators, Flex+Bison | Business day support |
| Maturity Rules (IMM, etc.) | All | - | Standard across all |
| Custom DSL Development | Lark, ANTLR-Java | - | Full grammar support |
| Education/Research | All | - | Parser comparison |

### By Integration Need

| Integration | Best Choice | Notes |
|-------------|-------------|-------|
| Java Application | **ANTLR-Java** | Native Java integration |
| Python Application | **Lark** | Native Python, no JVM |
| Scala Application | **Combinators** | Functional style |
| C Application | **Flex+Bison** | Only C option |
| Web Application (JS) | **Peggy** | JavaScript/Node.js |
| Clojure Application | **Instaparse** | Clojure-native |

---

## 📊 Feature Support Matrix

### Core Grammar Features (All Implementations Support)

✅ **Universal Features**
- Fixed dates: `25 DEC`, `1 JAN`
- Relative dates: `EASTER - 2D`, `EASTER + 39D`
- Nth weekday: `3 MON JAN`, `LAST MON MAY`
- Series/ranges: `[2 DEC : 3 DEC]`, `[7 JAN : 1M : 7 DEC]`
- Shifts: `> PRECEDING`, `> FOLLOWING`, `+ 1D`, `- 1D`
- Observance shifts: `{SAT - 1D, SUN + 1D}`
- Conditions: `IF DAY 8..14 APR`, `IF MON TUE WED THU`
- References: `@rule.key`

### Advanced Features (Partial Support)

| Feature | Lark | ANTLR-Java | ANTLR-Python | Others |
|---------|------|------------|--------------|--------|
| **ONE OF** | ✅ | ✅ | ✅ | ❌ |
| **ANNOUNCED BY** | ✅ | ✅ | ✅ | ❌ |
| **Lunar Calendars** | ✅ | ✅ | ✅ | ❌ |
| Calendar Declarations | ✅ | ✅ | ✅ | ❌ |

**Recommendation**: Use Lark, ANTLR-Java, or ANTLR-Python for full feature support.

---

## 🏃‍♂️ Getting Started

### For Python Users (Recommended: Lark)

```bash
# Clone the repository
cd date-rule-lark

# Install dependencies
pip install -e .

# Parse a date expression
from lark_schedule.rules import parse_rules

rules_text = "rule test for test in exchange:TEST = 25 DEC"
book = parse_rules(rules_text)

# Evaluate for a year
for occurrence in book.holidays("exchange:TEST", 2025):
    print(occurrence.date)
```

### For Java Users (Recommended: ANTLR-Java)

```bash
# Build with Maven
cd date-rule-antlr-java
mvn clean package

# Use in your Java code
import io.github.hjuergens.time.ScheduleParsers;

String expression = "25 DEC";
ScheduleParsers.ParserResult result = ScheduleParsers.parse(expression);
// Use result...
```

---

## 📊 Project Statistics

### Implementation Counts

| Category | Count | % of Total |
|----------|-------|------------|
| Production | 6 | 29% |
| Secondary | 4 | 19% |
| Experimental | 12 | 57% |
| **Total** | **21** | 100% |

### By Language

| Language | Count | % of Total |
|----------|-------|------------|
| Java | 11 | 52% |
| Python | 2 | 10% |
| C | 1 | 5% |
| Clojure | 1 | 5% |
| JavaScript | 1 | 5% |
| Scala | 1 | 5% |
| Haskell | 1 | 5% |
| **Total** | **18** | 86% |

*Note: Some implementations use multiple languages (e.g., Flex+Bison uses C but targets Java)*

### Test Coverage

| Test File | Cases | Coverage |
|-----------|-------|----------|
| `examples/schedule.txt` | 537 | Core grammar |
| `examples/catalog.txt` | 2,186 | Holidays-events catalog |
| `examples/date-holidays.txt` | 3,347 | Date holiday rules |
| **Total** | **6,070** | **Comprehensive** |

### Holidays-Events Validation

- **Rules**: 229
- **Years**: 2021-2031 (12 years)
- **Agreement Rate**: 100%
- **Differences**: 0

---

## 📁 File Structure

```
date-parser/
├── context/                    # This directory - Taxonomy framework
│   ├── TAXONOMY.md            # Full taxonomy, axes, dimensions
│   ├── taximony.json          # Machine-readable JSON version
│   └── QUICK_REFERENCE.md      # This file
│
├── docs/                      # Website generation
│   ├── index.html            # Generated website
│   └── generate.py           # Website generator
│
├── holidays-events/           # Market-relevant date rules
│   ├── us.rules              # US rules (45 rules)
│   ├── uk_ch.rules           # UK/CH rules (42 rules)
│   ├── apac_other.rules      # APAC rules (34 rules)
│   └── ...                   # 229 total rules
│
├── date-rule-lark/            # Lark implementation (Python)
│   └── src/lark_schedule/
│       ├── rules.lark       # Grammar
│       └── rules.py         # Parser
│
├── date-rule-antlr-java/      # ANTLR Java implementation
│   └── src/main/antlr4/...
│       └── DateSchedule.g4  # Grammar
│
├── date-rule-combinators/    # Scala combinators
│   └── src/main/scala/...
│       └── ScheduleParsers.scala
│
├── examples/                  # Test cases
│   ├── schedule.txt          # 537 cases
│   ├── catalog.txt           # 2,186 cases
│   └── date-holidays.txt     # 3,347 cases
│
└── date-rule-experiments/    # Experimental implementations
    ├── antlr-java/
    ├── bison/
    └── ...                   # 12 experiments
```

---

## 🔧 Maintenance Information

### Implementation Status

| Implementation | Last Commit | Status | Notes |
|---------------|-------------|--------|-------|
| Lark | 2026-11-16 | Active | Primary for holidays-events |
| ANTLR-Java | 2026-11-16 | Active | Reference implementation |
| ANTLR-Python | 2026-11-16 | Active | Python alternative |
| Combinators | 2026-10-27 | Active | Scala functional |
| Flex+Bison | 2026-11-16 | Active | C integration |
| SableCC | 2026-10-27 | Active | Java |

### Recommendations

1. **For New Projects**: Start with Lark (Python) or ANTLR-Java (Java)
2. **For Contributions**: Focus on production implementations
3. **For Research**: Explore experimental implementations
4. **For Legacy Systems**: Use secondary implementations

---

## 📚 Documentation

| Resource | Location | Description |
|----------|----------|-------------|
| Full Taxonomy | [`context/TAXONOMY.md`](./TAXONOMY.md) | Complete framework |
| JSON Version | [`context/taximony.json`](./taximony.json) | Machine-readable |
| Website | `https://hjuergens.github.io/date-parser/` | Generated docs |
| README | [`README.md`](../README.md) | Project overview |
| Holidays-Events | [`holidays-events/README.md`](../holidays-events/README.md) | Rule language spec |

---

## 🎓 Common Questions

### Q: Which implementation should I use?
**A**: See the [Implementation Selector](#-implementation-selector) above.

### Q: Are all implementations equivalent?
**A**: Yes, for core grammar features. Some advanced features (ONE OF, ANNOUNCED BY, lunar calendars) are only fully supported in Lark, ANTLR-Java, and ANTLR-Python.

### Q: How do I test my parser?
**A**: Run the shared test suites from `examples/` directory. All production implementations should pass all 6,070 test cases.

### Q: Can I add a new implementation?
**A**: Yes! Follow the existing patterns. Start by implementing support for `examples/schedule.txt` (537 cases), then add support for advanced features.

### Q: How do I validate against holidays-events?
**A**: Use the validation script in `date-rule-lark/tests/`. All 229 holidays-events rules should produce the same results as the reference engine for 2021-2031.

---

## 📞 Support

- **Issues**: Report on GitHub repository
- **Documentation**: See [TAXONOMY.md](./TAXONOMY.md)
- **Community**: Check README for community links

---

**Tip**: Bookmark this file for quick reference when working with the date-parser project!
