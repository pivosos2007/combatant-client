/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2-inspired implementation; not Adobe source code. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumProgressBar } from './progress-bar.js';
export class SpectrumMeter {
  static render(props={}) {
    const key=ui.str(props.key,'s2:meter'),palette=SpectrumFoundation.colors(props.colors);
    const variant=ui.str(props.variant,'informative'),fill=variant==='positive'?'#FF4BAF78':
      variant==='notice'?'#FFF7B955':variant==='negative'?palette.danger:palette.accent;
    return SpectrumProgressBar.render({...props,key,colors:{...palette,accent:fill}});
  }
}
