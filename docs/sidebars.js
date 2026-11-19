const sidebars = {
  sidebar: [
    "index",
    "getting-started",
    "concepts/which-profiler",
    "concepts/how-it-works",
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: [
        "reference/sampling-profiler",
        "reference/causal-profiler",
        "reference/cost-center",
        "reference/tagging-plugin",
        "reference/jmh-profiler"
      ]
    },
    {
      type: "category",
      label: "Tutorials",
      collapsed: true,
      items: [
        "tutorials/first-flamegraph",
        "tutorials/find-producer-consumer-bottleneck"
      ]
    },
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to/find-bottleneck-causal",
        "how-to/setup-tagging-plugin",
        "how-to/tag-effects-by-hand",
        "how-to/profile-running-application",
        "how-to/profile-jmh-benchmarks",
        "how-to/profile-part-of-program",
        "how-to/fix-empty-profiles"
      ]
    },
    "resources"
  ]
};

module.exports = sidebars;
