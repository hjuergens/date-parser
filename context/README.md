# context/ Directory

> **Purpose**: Centralized project taxonomy, classification framework, and reference documentation

## 📁 Contents

This directory contains framework documents that help understand, categorize, and navigate the date-parser project's complex multi-implementation architecture.

| File | Purpose | Audience | Format |
|------|---------|----------|--------|
| [`TAXONOMY.md`](./TAXONOMY.md) | Complete taxonomy, axes, and dimensions framework | Developers, Architects | Markdown |
| [`taximony.json`](./taximony.json) | Machine-readable version of the taxonomy | Tools, Scripts | JSON |
| [`QUICK_REFERENCE.md`](./QUICK_REFERENCE.md) | Quick lookup guide for implementation selection | Users, Developers | Markdown |

---

## 🎯 Framework Overview

The date-parser project consists of **21 parser implementations** across **7 programming languages**, each with different characteristics, maturity levels, and feature support. This complexity requires a structured approach to understanding and managing the project.

Our framework provides three complementary views:

### 1. **Taxonomy** (Hierarchical Classification)
Organizes the project into a tree structure:
```
date-parser/
├── Core System/
│   ├── Parser Implementations/
│   │   ├── Production (6 implementations)
│   │   └── Secondary (4 implementations)
│   ├── Runtime & Infrastructure/
│   └── Domain Applications/
│       └── holidays-events/ (229 rules)
└── Supporting Assets/
    ├── Examples & Test Data/
    ├── Experiments/ (12 implementations)
    └── Scripts & Utilities/
```

### 2. **Axes** (Orthogonal Dimensions)
Provides multiple independent ways to categorize components:
- **Parser Technology**: Generator vs Library vs Combinator
- **Language/Platform**: Java, Python, C, Clojure, JavaScript, Scala
- **Parsing Algorithm**: ALL(*), LALR(1), GLL, PEG, etc.
- **Maturity Level**: Production, Secondary, Experimental
- **Feature Completeness**: Full, Near-Full, Partial, Minimal
- **Performance**: Linear, Quadratic, Cubic
- **Use Case Specialization**: Holidays-Events, Financial, General, etc.

### 3. **Dimensions** (Detailed Attributes)
Fine-grained characteristics for comparison:
- Grammar Capabilities (12 features)
- Input/Output Characteristics
- Integration Characteristics
- Testing & Quality Metrics
- Maintenance Metrics
- Use Case Alignment

---

## 📊 Quick Facts

### Project Scale
- **Total Implementations**: 21
- **Production Implementations**: 6
- **Secondary Implementations**: 4
- **Experimental Implementations**: 12
- **Languages**: 7 (Java, Python, C, Clojure, JavaScript, Scala, Haskell)
- **Test Cases**: 6,070
- **Holidays-Events Rules**: 229

### Validation
- **Validation Period**: 2021-2031 (12 years)
- **Agreement Rate**: 100%
- **Differences**: 0

---

## 🎨 Visualization

The framework enables several visual representations:

### 2D: Technology vs Maturity
```
Maturity
  ↑
  │ Production  ● Lark
  │              ● ANTLR-Java
  │              ● ANTLR-Python
  │
  │ Secondary    ● Bison
  │
  └──────────────────────────────▶
       Java  Python  C  Clojure  JS  Scala
     Technology/Platform
```

### 3D: Technology × Maturity × Feature Completeness
- X-axis: Technology/Platform
- Y-axis: Maturity
- Z-axis: Feature Completeness
- Size: Code size
- Color: Performance

---

## 🔧 Usage

### For Project Maintainers
1. **Decision Making**: Use the taxonomy to decide which implementations to maintain
2. **Prioritization**: Focus on production implementations (6 out of 21)
3. **Resource Allocation**: Allocate resources based on maturity and importance
4. **Roadmap Planning**: Use axes to identify gaps and opportunities

### For Developers
1. **Implementation Selection**: Use the quick reference to choose the right implementation
2. **Feature Check**: Verify which features are supported in your chosen implementation
3. **Contribution**: Identify gaps in experimental implementations to contribute
4. **Testing**: Ensure your changes pass all 6,070 test cases

### For Users
1. **Quick Selection**: Use the decision tree to find the right implementation
2. **Feature Verification**: Check feature support matrix for your requirements
3. **Integration**: Find examples and guides for your use case
4. **Troubleshooting**: Use the taxonomy to understand implementation differences

### For Tools & Scripts
1. **Automation**: Use `taximony.json` for programmatic access
2. **Validation**: Automate testing across all implementations
3. **Reporting**: Generate reports and dashboards from the JSON data
4. **Documentation**: Auto-generate documentation from the framework

---

## 📖 Documentation Structure

### [`TAXONOMY.md`](./TAXONOMY.md)
The **comprehensive** framework document containing:
- Complete hierarchical taxonomy
- All 7 axes with detailed categories
- All dimensions with feature matrices
- Strategic insights and recommendations
- Visual framework descriptions
- Analysis by category

**Use this for**: Deep understanding, architecture decisions, project planning

### [`taximony.json`](./taximony.json)
Machine-readable JSON representation of the framework containing:
- Complete taxonomy structure
- All axes and categories
- Implementation details
- Recommendations

**Use this for**: Automation, tooling, programmatic access, validation scripts

### [`QUICK_REFERENCE.md`](./QUICK_REFERENCE.md)
Concise guide for **daily use** containing:
- Implementation selector (decision tree)
- Comparison tables
- Feature support matrix
- Getting started examples
- Project statistics
- File structure overview

**Use this for**: Quick lookups, implementation selection, getting started

---

## 🚀 Getting Started with the Framework

### 1. Read the Quick Reference
Start with [`QUICK_REFERENCE.md`](./QUICK_REFERENCE.md) to understand:
- Which implementation to use for your needs
- Key differences between implementations
- Basic project statistics

### 2. Explore the Taxonomy
Dive into [`TAXONOMY.md`](./TAXONOMY.md) to understand:
- The complete hierarchical structure
- How components relate to each other
- Strategic insights about the project

### 3. Use the JSON for Automation
Leverage [`taximony.json`](./taximony.json) to:
- Build tools that understand the project structure
- Automate testing across implementations
- Generate custom reports

---

## 🔄 Maintenance

### Updating the Framework
When the project changes (new implementations, deprecated implementations, feature additions):

1. **Update TAXONOMY.md**
   - Add new implementations to the hierarchy
   - Update axes categories
   - Revise dimensions and feature matrices

2. **Update taximony.json**
   - Add new entries to the JSON structure
   - Ensure consistency with TAXONOMY.md

3. **Update QUICK_REFERENCE.md**
   - Revise comparison tables
   - Update quick selectors
   - Refresh statistics

### Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-11-20 | Initial framework creation | Vibe Assistant |

---

## 📞 Related Resources

- **Project README**: [`../README.md`](../README.md) - Main project documentation
- **Holidays-Events**: [`../holidays-events/README.md`](../holidays-events/README.md) - Rule language specification
- **Website**: `https://hjuergens.github.io/date-parser/` - Generated project website
- **Test Cases**: [`../examples/`](../examples/) - Shared test suites

---

## 🎓 Concepts Explained

### What is a Taxonomy?
A **taxonomy** is a hierarchical classification system that organizes items into groups based on their characteristics. In this project, we classify parser implementations by their purpose, maturity, and capabilities.

### What are Axes?
**Axes** are orthogonal (independent) dimensions that provide different ways to categorize the same items. For example, you can categorize implementations by language (Java, Python) or by algorithm (LALR, LL) independently.

### What are Dimensions?
**Dimensions** are detailed attributes that describe specific characteristics. For example, the "Grammar Capabilities" dimension lists which grammar features each implementation supports.

### Why This Framework?
The date-parser project's complexity (21 implementations, 7 languages, thousands of test cases) requires a structured approach to:
1. **Understand** the project's components
2. **Compare** implementations objectively
3. **Select** the right tool for the job
4. **Maintain** the codebase efficiently
5. **Plan** future development

---

## 💡 Best Practices

### For Users
✅ **Start with production implementations** (Lark, ANTLR-Java, ANTLR-Python)
✅ **Check feature support** before committing to an implementation
✅ **Use the test suites** to validate your choice
✅ **Contribute back** improvements and bug fixes

### For Developers
✅ **Maintain feature parity** across production implementations
✅ **Document differences** between implementations
✅ **Keep test suites synchronized**
✅ **Update this framework** when making changes

### For Maintainers
✅ **Focus on production implementations** (6 out of 21)
✅ **Consider archiving** experimental implementations
✅ **Automate testing** across all implementations
✅ **Monitor validation** against holidays-events

---

**Next Steps**:
1. ✅ Read [`QUICK_REFERENCE.md`](./QUICK_REFERENCE.md) for implementation selection
2. ✅ Explore [`TAXONOMY.md`](./TAXONOMY.md) for deep understanding
3. ✅ Use [`taximony.json`](./taximony.json) for programmatic access
4. ✅ Check the project [`README.md`](../README.md) for general information
