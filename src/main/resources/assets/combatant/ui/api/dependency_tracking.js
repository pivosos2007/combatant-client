/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

// Installed once per V8 isolate. The render function remains the module's own
// function. Tracking is enabled only for callers using bakeTracked().
globalThis.__combatant_render_tracked = function (context) {
  const MAX_DEPENDENCIES = 2048;
  const dependencies = new Map();
  const proxies = new WeakMap();
  let overflow = false;
  const escaped = new Set();

  function read(kind, path) {
    if (overflow) return;
    const key = JSON.stringify([kind, path]);
    if (dependencies.has(key)) return;
    if (dependencies.size >= MAX_DEPENDENCIES) {
      overflow = true;
      dependencies.clear();
      return;
    }
    dependencies.set(key, [kind, path]);
  }

  function wrap(value, path) {
    if (value === null || typeof value !== 'object') return value;
    const proxy = new Proxy(value, {
      get(target, property, receiver) {
        if (typeof property === 'symbol') return Reflect.get(target, property, receiver);
        const child = Reflect.get(target, property, receiver);
        // Methods are stable on JavaScript's native arrays, and map()/forEach()
        // will independently record the array elements and length they read.
        if (Array.isArray(target) && typeof child === 'function' &&
            Object.prototype.hasOwnProperty.call(Array.prototype, property)) return child;
        const next = path.concat(String(property));
        read('value', next);
        return wrap(child, next);
      },
      has(target, property) {
        if (typeof property !== 'symbol') read('has', path.concat(String(property)));
        return Reflect.has(target, property);
      },
      ownKeys(target) {
        read('keys', path);
        return Reflect.ownKeys(target);
      },
      getOwnPropertyDescriptor(target, property) {
        return Reflect.getOwnPropertyDescriptor(target, property);
      }
    });
    proxies.set(proxy, { target: value, path });
    return proxy;
  }

  // Never return tracked proxies to the native object converter. A returned
  // object may itself be a live props object, even if the script never reads
  // its individual fields. Recording the escaped path keeps that dependency.
  const visited = new WeakMap();
  function unwrap(value) {
    if (value === null || typeof value !== 'object') return value;
    const source = proxies.get(value);
    if (source) {
      read('value', source.path);
      escaped.add(JSON.stringify(source.path));
      value = source.target;
    }
    if (visited.has(value)) return visited.get(value);
    const isArray = Array.isArray(value);
    const copy = isArray ? new Array(value.length) : {};
    visited.set(value, copy);
    let changed = source !== undefined;
    for (const key of Object.keys(value)) {
      const original = value[key];
      const clean = unwrap(original);
      if (clean !== original) changed = true;
      copy[key] = clean;
    }
    // Preserve the original tree if it contained no tracked proxy at all.
    // Cyclic UI trees are rejected downstream, just as on the old render path.
    if (!changed) {
      visited.set(value, value);
      return value;
    }
    return copy;
  }

  const tree = unwrap(globalThis.__combatant_render(wrap(context, [])));
  if (overflow) return { tree, dependencies: null };

  // Reading ctx.props followed by ctx.props.foo must not turn every change
  // to an unrelated prop into a dependency. Nested properties are independent;
  // a path which escaped into the output was recorded above and is retained.
  const all = Array.from(dependencies.values());
  const hasSpecificPropsRead = all.some(d => d[1][0] === 'props' &&
    (d[1].length > 1 || d[0] === 'keys'));
  const filtered = hasSpecificPropsRead
    ? all.filter(d => !(d[0] === 'value' && d[1].length === 1 && d[1][0] === 'props'
        && !escaped.has(JSON.stringify(d[1]))))
    : all;
  return { tree, dependencies: filtered };
};
