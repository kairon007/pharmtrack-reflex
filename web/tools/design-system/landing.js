/**
 * Landing page design system — page behaviour.
 *
 * The page behaviour module: hero word rotation, upcoming-stack tilt and
 * the pricing-section icon pile. The original was a 2 526-byte
 * minified script; REA's JavaScript analysis recorded it as a single
 * `source-module` with no import/export relationships and confirmed the
 * two timers it schedules: a `setInterval` at 2 400 ms and a `setTimeout`
 * at 300 ms.
 *
 * Three behaviours live here:
 *   1. heroWordRotation  — swaps the italicised hero word and re-tilts
 *                           the upcoming-events stack every 2.4 s
 *   2. upcomingStackTilt — fans the `[data-ev]` cards back and forth
 *   3. iconPilePhysics   — a small rigid-body simulation for the icon
 *                           pile in the pricing section, with pointer
 *                           repulsion
 *
 * All three are skipped when the visitor asks for reduced motion, exactly
 * as in the original.
 *
 * The original used single-letter identifiers (e / t / n / r / i / a / l / u
 * / d / f). They are expanded below; every numeric constant, threshold and
 * ordering is preserved verbatim.
 */

const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Words rotated through the hero headline. */
const HERO_WORDS = ["day", "habits", "tasks", "routines", "goals"];

/** The slot in the hero headline the rotating word is written into. */
const heroWordSlot = document.querySelector("[data-hero-word]");

/** The stack of upcoming-event cards in the "what's next" section. */
const eventCards = Array.from(document.querySelectorAll("[data-ev]"));

/**
 * Fan the upcoming-event cards into a diagonal stack.
 *
 * Each card's depth is its distance from the front of the stack; depth
 * drives the offset, the rotation and the stacking order, so card 0 always
 * sits in front and the rest recede up and to the left.
 *
 * @param {number} offset Rotation step. The card at index `i` is drawn at
 *   depth `(i + offset) % eventCards.length`.
 */
function tiltUpcomingStack(offset) {
  eventCards.forEach((card, index) => {
    const depth = (index + offset) % eventCards.length;
    card.style.transform = `translate(${-depth * 10}px, ${-depth * 6}px) rotate(${5 - depth * 6}deg)`;
    card.style.zIndex = String(20 - depth);
  });
}

/**
 * Build the physics simulation for the pricing-section icon pile.
 *
 * The pile is a set of round icon chips that drop into a tray, collide with
 * each other and with the tray walls, and get shoved around by the pointer.
 * It only runs while the section is on screen.
 */
function initIconPile() {
  const section = document.querySelector(".s-get");
  const pile = section?.querySelector(".s-ci-pile");
  if (!section || !pile) return;

  const chips = Array.from(pile.querySelectorAll(".s-ci"));

  /** @type {Array<{el: Element, r: number, x: number, y: number, vx: number, vy: number, a: number, va: number}>} */
  let bodies = [];
  let frameId = 0;
  let lastTime = 0;
  let running = false;
  let lastSpawn = 0;
  /** @type {{x: number, y: number} | null} Pointer position in pile space. */
  let pointer = null;

  /** Drop a fresh set of chips in from above the tray. */
  const spawn = () => {
    lastSpawn = Date.now();
    pile.classList.add("phys");

    const width = pile.clientWidth;
    bodies = chips.map((chip, index) => {
      const radius = chip.offsetWidth / 2;
      return {
        el: chip,
        r: radius,
        // Spread the chips across the tray with a fractional stride, then
        // stagger them vertically so they arrive one after another.
        x: radius + ((index * 0.37 + 0.11) % 1) * Math.max(1, width - 2 * radius),
        y: -radius - 140 * index - Math.random() * 60,
        vx: (Math.random() - 0.5) * 60,
        vy: 0,
        a: (Math.random() - 0.5) * 40, // current rotation
        va: 0, // angular velocity
      };
    });

    bodies.forEach((body) => {
      body.el.style.transform = `translate(${body.x - body.r}px,${body.y - body.r}px)`;
    });
  };

  /**
   * One animation frame. Four physics sub-steps run per frame so the
   * simulation stays stable regardless of the frame rate.
   *
   * @param {number} now High-resolution timestamp from requestAnimationFrame.
   */
  const step = (now) => {
    frameId = requestAnimationFrame(step);

    const dt = Math.min(0.033, (now - (lastTime || now)) / 1e3);
    if ((lastTime = now), !bodies.length || dt <= 0) return;

    const width = pile.clientWidth;
    const height = pile.clientHeight;
    const scale = dt / 4;

    for (let substep = 0; substep < 4; substep++) {
      // Integrate every body: gravity, pointer repulsion, damping, walls.
      for (const body of bodies) {
        body.vy += 2200 * scale;

        if (pointer) {
          const dx = body.x - pointer.x;
          const dy = body.y - pointer.y;
          const distance = Math.hypot(dx, dy);
          const reach = body.r + 46;
          if (distance < reach && distance > 0.01) {
            const push = ((reach - distance) / reach) * 9e3 * scale;
            body.vx += (dx / distance) * push;
            body.vy += (dy / distance) * push;
          }
        }

        body.vx *= 0.999;
        body.x += body.vx * scale;
        body.y += body.vy * scale;
        body.a += body.va * scale;
        body.va *= 0.98;

        // Floor: land and bounce with a low coefficient of restitution.
        if (body.y > height - body.r) {
          body.y = height - body.r;
          if (body.vy > 0) body.vy *= -0.32;
        }

        body.vx *= 0.9;
        body.va = body.vx * 0.6;

        // Side walls.
        if (body.x < body.r) {
          body.x = body.r;
          body.vx = Math.abs(body.vx) * 0.4;
        }
        if (body.x > width - body.r) {
          body.x = width - body.r;
          body.vx = -Math.abs(body.vx) * 0.4;
        }
      }

      // Resolve body-vs-body collisions.
      for (let i = 0; i < bodies.length; i++) {
        for (let j = i + 1; j < bodies.length; j++) {
          const a = bodies[i];
          const b = bodies[j];
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const distance = Math.hypot(dx, dy) || 0.01;
          const minDistance = a.r + b.r;

          if (distance < minDistance) {
            // Separate the pair along the contact normal.
            const nx = dx / distance;
            const ny = dy / distance;
            const overlap = (minDistance - distance) / 2;
            a.x -= nx * overlap;
            a.y -= ny * overlap;
            b.x += nx * overlap;
            b.y += ny * overlap;

            // Only exchange momentum along a closing normal.
            const closing = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
            if (closing < 0) {
              const impulse = (-1.3 * closing) / 2;
              a.vx -= impulse * nx;
              a.vy -= impulse * ny;
              b.vx += impulse * nx;
              b.vy += impulse * ny;
              // Kick the chips into a spin so they settle at an angle.
              a.va -= (b.vx - a.vx) * 0.2;
              b.va += (b.vx - a.vx) * 0.2;
            }
          }
        }
      }
    }

    for (const body of bodies) {
      body.el.style.transform =
        `translate(${(body.x - body.r).toFixed(1)}px,${(body.y - body.r).toFixed(1)}px) ` +
        `rotate(${body.a.toFixed(1)}deg)`;
    }
  };

  const start = () => {
    if (!frameId) {
      lastTime = 0;
      requestAnimationFrame(step);
    }
  };

  const stop = () => {
    cancelAnimationFrame(frameId);
    frameId = 0;
  };

  // Run the simulation only while the section is at least 35 % visible.
  new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          if (!running && entry.intersectionRatio >= 0.35) {
            running = true;
            spawn();
          }
          if (running) start();
        } else {
          stop();
        }
      }
    },
    { threshold: [0, 0.35] },
  ).observe(section);

  // Pointer repulsion, in the pile's own coordinate space.
  section.addEventListener("pointermove", (event) => {
    const rect = pile.getBoundingClientRect();
    const ratio = rect.width / pile.clientWidth || 1;
    pointer = { x: (event.clientX - rect.left) / ratio, y: (event.clientY - rect.top) / ratio };
  });

  section.addEventListener("pointerleave", () => {
    pointer = null;
  });

  // Re-drop the chips if the visitor comes back to a settled pile.
  section.addEventListener("pointerenter", () => {
    if (running && Date.now() - lastSpawn > 2500) spawn();
  });
}

if (!prefersReducedMotion) {
  let wordIndex = 0;

  // Rotate the hero word and re-tilt the upcoming stack on every tick.
  setInterval(() => {
    wordIndex += 1;

    if (heroWordSlot) {
      const word = document.createElement("i");
      word.className = "s-hw";
      word.textContent = `${HERO_WORDS[wordIndex % HERO_WORDS.length]},`;
      heroWordSlot.replaceChildren(word);
    }

    tiltUpcomingStack(wordIndex);
  }, 2400);

  // Let the page settle before the icon pile starts falling.
  setTimeout(initIconPile, 300);
}
