// Generates the default WiLauncher touch layout (FCL/src/main/assets/controllers/00000000.json).
//   node scripts/build-controls.mjs
// Positions are FCL's 0..1000 grid (share of the free space left of / above the view); sizes are
// tenths of a percent of the screen height. Keycodes are LWJGL ones, 1000/1002 are mouse buttons.
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'FCL/src/main/assets/controllers/00000000.json');

// Signed 32-bit ARGB, as Android stores colours.
const argb = (hex) => { const v = parseInt(hex, 16); return v >= 0x80000000 ? v - 0x100000000 : v; };
// Stable ids so the generated file doesn't change between runs.
const uuid = (name) => {
  const h = createHash('sha1').update(`wilauncher-controls:${name}`).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20, 32)}`;
};

const PURPLE = 'CC8B5CF6';
const buttonStyle = (name, textSize, cornerRadius) => ({
  name,
  textColor: -1,
  textSize,
  strokeColor: argb(PURPLE),
  strokeWidth: 6,
  cornerRadius,
  fillColor: argb('55000000'),
  textColorPressed: -1,
  textSizePressed: textSize,
  strokeColorPressed: argb('FFA78BFA'),
  strokeWidthPressed: 8,
  cornerRadiusPressed: cornerRadius,
  fillColorPressed: argb('AA8B5CF6'),
});

const noEvent = () => ({ autoKeep: false, autoClick: false, openMenu: false, switchTouchMode: false, input: false, quickInput: false, outputText: '', outputKeycodes: [], bindViewGroup: [] });

function button(name, text, { x, y, size, visibility = 'IN_GAME', style = 'WiLauncher', press, click, follow = false }) {
  const pressEvent = noEvent();
  const clickEvent = noEvent();
  if (press) pressEvent.outputKeycodes = press;
  if (click) Object.assign(clickEvent, click);
  return {
    id: uuid(name),
    text,
    style,
    baseInfo: {
      visibilityType: visibility,
      xPosition: x,
      yPosition: y,
      sizeType: 'PERCENTAGE',
      absoluteWidth: 50,
      absoluteHeight: 50,
      percentageWidth: { reference: 'SCREEN_HEIGHT', size },
      percentageHeight: { reference: 'SCREEN_HEIGHT', size },
    },
    event: { pointerFollow: follow, Movable: false, pressEvent, longPressEvent: noEvent(), clickEvent, doubleClickEvent: noEvent() },
  };
}

const KEY = { ESC: 1, BACKSPACE: 14, Q: 16, E: 18, T: 20, ENTER: 28, LCTRL: 29, LSHIFT: 42, SPACE: 57, F5: 63, LMB: 1000, RMB: 1002 };

const buttons = [
  // In game the top row sits in the middle (chat, pause, camera), so the top-left corner stays free for
  // minimap mods. In menus: Esc / keyboard on the left, Enter / Backspace on the right.
  button('back', '❚❚', { x: 0, y: 0, size: 105, visibility: 'MENU', style: 'WiLauncher Small', press: [KEY.ESC] }),
  button('chat', '💬', { x: 380, y: 0, size: 105, style: 'WiLauncher Small', press: [KEY.T] }),
  button('pause', '❚❚', { x: 500, y: 0, size: 105, style: 'WiLauncher Small', press: [KEY.ESC] }),
  button('perspective', '👁', { x: 620, y: 0, size: 105, style: 'WiLauncher Small', press: [KEY.F5] }),
  button('keyboard', '⌨', { x: 120, y: 0, size: 105, visibility: 'MENU', style: 'WiLauncher Small', click: { input: true } }),
  button('enter', '⏎', { x: 880, y: 0, size: 105, visibility: 'MENU', style: 'WiLauncher Small', press: [KEY.ENTER] }),
  button('backspace', '⌫', { x: 1000, y: 0, size: 105, visibility: 'MENU', style: 'WiLauncher Small', press: [KEY.BACKSPACE] }),
  // Right thumb: jump is the biggest; attack / use keep turning the camera while held.
  button('jump', '▲', { x: 985, y: 870, size: 170, press: [KEY.SPACE] }),
  button('attack', '⚔', { x: 870, y: 640, size: 140, press: [KEY.LMB], follow: true }),
  button('use', '✋', { x: 985, y: 560, size: 140, press: [KEY.RMB], follow: true }),
  button('sneak', '▼', { x: 860, y: 960, size: 120, click: { autoKeep: true, outputKeycodes: [KEY.LSHIFT] } }),
  button('inventory', '🎒', { x: 740, y: 975, size: 115, press: [KEY.E] }),
  button('drop', '⇣', { x: 985, y: 330, size: 105, click: { outputKeycodes: [KEY.Q] } }),
  // Left thumb: sprint toggle above the stick.
  button('sprint', '»', { x: 40, y: 400, size: 115, click: { autoKeep: true, outputKeycodes: [KEY.LCTRL] } }),
];

const stick = {
  id: uuid('stick'),
  style: 'WiLauncher',
  baseInfo: {
    visibilityType: 'IN_GAME',
    xPosition: 40,
    yPosition: 930,
    sizeType: 'PERCENTAGE',
    absoluteWidth: 50,
    absoluteHeight: 50,
    percentageWidth: { reference: 'SCREEN_HEIGHT', size: 440 },
    percentageHeight: { reference: 'SCREEN_HEIGHT', size: 440 },
  },
  event: { upKeycode: [17], downKeycode: [31], leftKeycode: [30], rightKeycode: [32], followOption: 'CENTER_FOLLOW', sneak: false, sneakKeycode: KEY.LSHIFT },
};

const layout = {
  id: '00000000',
  name: 'WiLauncher',
  version: '1.0.0',
  versionCode: 1,
  author: 'WiLauncher',
  description: 'Touch layout of WiLauncher: stick on the left, jump / attack / use under the right thumb.',
  controllerVersion: 21,
  buttonStyles: [buttonStyle('WiLauncher', 22, 100), buttonStyle('WiLauncher Small', 17, 40)],
  directionStyles: [{
    name: 'WiLauncher',
    styleType: 'ROCKER',
    buttonStyle: { interval: 50, ...Object.fromEntries(Object.entries(buttonStyle('', 14, 100)).filter(([k]) => k !== 'name')) },
    rockerStyle: {
      rockerSize: 420,
      bgCornerRadius: 500,
      bgStrokeWidth: 8,
      bgStrokeColor: argb('998B5CF6'),
      bgFillColor: argb('33000000'),
      rockerCornerRadius: 500,
      rockerStrokeWidth: 6,
      rockerStrokeColor: argb('FFA78BFA'),
      rockerFillColor: argb('AA8B5CF6'),
    },
  }],
  viewGroups: [{
    id: uuid('group'),
    name: 'WiLauncher',
    visibility: 'VISIBLE',
    viewData: { buttonList: buttons, directionList: [stick] },
  }],
};

writeFileSync(out, JSON.stringify(layout, null, 1) + '\n');
console.log(`layout with ${buttons.length} buttons + stick -> ${out}`);
