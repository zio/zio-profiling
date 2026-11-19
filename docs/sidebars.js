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
        "reference/cost-center"
      ]
    },
    "tutorials/first-flamegraph",
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to/find-bottleneck-causal",
        "how-to/setup-tagging-plugin",
        "how-to/tag-effects-by-hand",
        "how-to/profile-running-application",
        "how-to/profile-jmh-benchmarks"
      ]
    },
    "resources"
  ]
};

module.exports = sidebars;
