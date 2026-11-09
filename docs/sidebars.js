const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "Getting Started",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "resources"
      ]
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      link: { type: "doc", id: "reference/sampling-profiler" },
      items: [
        "reference/sampling-profiler",
        "reference/causal-profiler",
        "reference/cost-center"
      ]
    },
    {
      type: "category",
      label: "Tutorial",
      collapsed: false,
      link: { type: "doc", id: "tutorials/first-flamegraph" },
      items: [
        "tutorials/first-flamegraph"
      ]
    },
    {
      type: "category",
      label: "How-to Guides",
      collapsed: false,
      link: { type: "doc", id: "how-to/find-bottleneck-causal" },
      items: [
        "how-to/find-bottleneck-causal",
        "how-to/setup-tagging-plugin",
        "how-to/tag-effects-by-hand",
        "how-to/profile-running-application",
        "how-to/profile-jmh-benchmarks"
      ]
    }
  ]
};

module.exports = sidebars;
