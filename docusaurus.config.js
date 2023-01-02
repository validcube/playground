// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require("prism-react-renderer/themes/github");
const darkCodeTheme = require("prism-react-renderer/themes/dracula");

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: "ReVanced",
  tagline: "a",
  url: "https://docs.revanced.app",
  baseUrl: "/", // ig this could be used if we want to host it at https://revanced.app/docs
  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",
  favicon: "img/logo.svg",

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: "revanced", // Usually your GitHub org/user name.
  projectName: "revanced-docs", // Usually your repo name.

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: "en",
    locales: ["en"],
  },

  presets: [
    [
      "classic",
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          routeBasePath: "/",
        },
        theme: {
          customCss: require.resolve("./src/css/custom.css"),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: "ReVanced Docs",
        logo: {
          alt: "ReVanced logo",
          src: "img/logo.svg",
        },
        items: [
          {
            type: "doc",
            docId: "revanced-development/README",
            position: "left",
            label: "Development",
          },
          {
            type: "doc",
            docId: "revanced-manager/README",
            position: "left",
            label: "Manager",
          },
          {
            type: "doc",
            docId: "revanced-patches/README",
            position: "left",
            label: "Patches",
          },
          {
            href: "https://github.com/revanced/revanced-documentation",
            label: "GitHub",
            position: "right",
          },
        ],
      },
      footer: {
        style: "dark",
        links: [
          {
            title: "Docs",
            items: [
              {
                label: "Development",
                to: "/revanced-development",
              },
              {
                label: "Manager",
                to: "/revanced-manager",
              },
              {
                label: "Patches",
                to: "/revanced-patches",
              },
            ],
          },
          {
            title: "Community",
            items: [
              {
                label: "Discord",
                href: "https://revanced.app/discord",
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} ReVanced maintainers. Built with Docusaurus.`,
      },
      colorMode: {
        defaultMode: "dark",
        disableSwitch: true,
        respectPrefersColorScheme: false,
      },
      prism: {
        theme: darkCodeTheme,
        additionalLanguages: ["kotlin"],
      },
    }),
};

module.exports = config;
