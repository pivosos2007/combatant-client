/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import { SolidBrowserSurface } from "../../api/components/solid_browser.js";

function num(value, fallback) {
  const out = Number(value);
  return Number.isFinite(out) ? out : fallback;
}

function str(value, fallback = "") {
  return typeof value === "string" && value.length ? value : fallback;
}

function svg(key, asset) {
  return ui.svg({
    key,
    asset: str(asset, "settings-2"),
    tint: "#D8FFFFFF",
    interactive: false,
    class: ui.abs(0, 0, 9, 9),
  });
}

function categoryList(value) {
  if (!value) return [];
  try { return Array.from(value); } catch (_) { return []; }
}

export function buildTemplate(ctx) {
  const p = ctx.props || {};
  const width = Math.max(720, num(p.width, 976));
  const height = Math.max(470, num(p.height, 636));
  const accent = str(p.accent, "#FF906BFF");
  const categories = categoryList(p.categories);
  const selected = str(p.selectedCategory, categories.length ? str(categories[0].id, "general") : "general");
  const search = str(p.search, "");
  const tokens = SolidBrowserSurface.tokens();

  const navigationItems = categories.map((category, index) => SolidBrowserSurface.navigationItem({
    key: `map-settings:nav:${str(category.id, index.toString())}`,
    selected: str(category.id, "") === selected,
    accent,
    width: 252,
    height: 46,
    label: str(category.label, "Map"),
    icon: svg(`map-settings:nav-icon:${index}`, category.icon),
  }));

  const selectedCategory = categories.find((category) => str(category.id, "") === selected) || categories[0] || {};
  const title = str(selectedCategory.label, "World Map");
  const description = str(selectedCategory.description, "Xaero World Map settings");

  return SolidBrowserSurface.surface({
    key: "map-settings",
    width,
    height,
    accent,
    combinedNavigation: true,
    navigationHeader: ui.svg({
      key: "map-settings:logo",
      asset: "map",
      tint: "#E8FFFFFF",
      interactive: false,
      class: ui.abs(0, 0, 11, 11),
    }),
    navigationItems,
    toolbarLeft: SolidBrowserSurface.searchField({
      key: "map-settings:search",
      value: search,
      placeholder: "Search settings",
      accent,
    }),
    toolbarLeftWidth: tokens.searchWidth,
    detailActive: true,
    detailBackdrop: false,
    detailTitle: title,
    detailDescription: description,
    detailClose: SolidBrowserSurface.closeButton({
      key: "map-settings:close",
      accent,
      icon: svg("map-settings:close-icon", "x"),
    }),
    detailContent: [],
  });
}
