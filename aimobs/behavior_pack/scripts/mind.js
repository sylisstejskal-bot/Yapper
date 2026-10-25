const CHEST_WORDS = ["treasure", "gold", "chest", "loot", "coins", "jewel"];
const WATER_WORDS = ["water", "swim", "swimming", "fish", "rain", "sea", "ocean", "puddle"];
const MONSTER_WORDS = ["monster", "monsters", "mob", "mobs", "zombie", "hostile", "guard", "guards", "protect", "protects", "slay", "hunt", "hunts"];

const STYLES = [
  ["pirate", ["pirate", "buccaneer", "sailor"]],
  ["cowboy", ["cowboy", "cowgirl", "outlaw"]],
  ["robot", ["robot", "android", "cyborg"]],
  ["shakespeare", ["shakespeare", "elizabethan"]],
  ["poet", ["poet", "poem", "haiku", "verse"]],
  ["caps", ["all caps", "shouting", "yells"]],
];

const MOODS = [
  ["grumpy", ["grumpy", "angry", "cranky"]],
  ["cheerful", ["cheerful", "happy", "friendly", "kind", "excited"]],
  ["shy", ["shy", "timid", "quiet"]],
  ["sarcastic", ["sarcastic", "sardonic"]],
  ["mean", ["mean", "rude", "cruel"]],
  ["dramatic", ["dramatic", "theatrical"]],
  ["helpful", ["helpful", "helpful", "kindly"]],
];

export function plan(personality, senses) {
  const parsed = parsePersonality(personality);
  const choice = choose(parsed, senses);
  const speech = (senses.tick + senses.salt) % 80 === 0 ? speak(parsed, choice, senses) : null;
  return {
    focus: choice.focus,
    intent: choice.intent,
    speed: choice.speed,
    attack: choice.attack,
    speech,
  };
}

export function parsePersonality(personality) {
  const raw = String(personality ?? "").trim().replace(/\s+/g, " ");
  const lower = raw.toLowerCase();
  return {
    raw,
    style: firstCue(lower, STYLES) ?? "plain",
    mood: firstCue(lower, MOODS) ?? "neutral",
    loves: phrases(lower, /(?:loves|likes|obsessed with|adores|lives for)\s+([^.;!]+)/g),
    hates: phrases(lower, /(?:hates|dislikes|loathes|can't stand|cannot stand)\s+([^.;!]+)/g),
    onlySubject: firstPhrase(lower, /(?:only talks about|only talk about|only cares about|only care about)\s+([^.;!]+)/),
    stand: /\b(stand still|stands still|don't move|do not move|stays put|wait here)\b/.test(lower),
    attackPlayers: /\b(attacks? players?|hunts? players?|hostile to players?|kills? players?)\b/.test(lower),
    attackMonsters: /\b(attacks? (monsters?|mobs?|zombies?)|hunts? (monsters?|mobs?)|guards?|protects?|slays?)\b/.test(lower),
    flee: /\b(runs? away|flees?|scared of|afraid of|shy|timid)\b/.test(lower),
    follow: /\b(follows?|loyal|companion|stays with|follows? me|follows? the player)\b/.test(lower),
  };
}

function choose(parsed, senses) {
  if (parsed.stand) return motion("stand", "stand", 0, false);
  if (parsed.attackPlayers && senses.playerKnown) {
    const close = senses.playerDistance <= 2.5;
    return motion("player", "chase", close ? 0 : 0.16, close);
  }
  if (parsed.attackMonsters) {
    if (senses.hostileKnown) {
      const close = senses.hostileDistance <= 2.5;
      return motion("hostile", "hunt", close ? 0 : 0.16, close);
    }
    return motion("wander", "wander", 0.08, false);
  }
  if (parsed.flee || parsed.mood === "shy") {
    if (senses.playerKnown && senses.playerDistance < 12) return motion("away", "flee", 0.2, false);
    return motion("wander", "wander", 0.07, false);
  }
  const subject = parsed.onlySubject || parsed.loves[0] || "";
  if (subject && senses.chestKnown && mentions(subject, CHEST_WORDS)) {
    return motion("chest", "seek", senses.chestDistance < 1.8 ? 0 : 0.12, false);
  }
  if (subject && senses.waterKnown && mentions(subject, WATER_WORDS)) {
    return motion("water", "seek", senses.waterDistance < 1.8 ? 0 : 0.12, false);
  }
  if (parsed.follow || parsed.mood === "cheerful" || parsed.mood === "helpful") {
    if (senses.playerKnown) {
      return motion("player", "follow", senses.playerDistance < 2.8 ? 0 : 0.12, false);
    }
  }
  if (parsed.mood === "grumpy" || parsed.mood === "mean") return motion("wander", "wander", 0.06, false);
  return motion("wander", "wander", 0.08, false);
}

function motion(focus, intent, speed, attack) {
  return { focus, intent, speed, attack };
}

function speak(parsed, choice, senses) {
  const subject = parsed.onlySubject || parsed.loves[0] || "";
  const name = senses.playerName || "friend";
  let line;
  if (parsed.onlySubject && choice.focus !== "chest" && choice.focus !== "water") {
    line = `${name}, that is not ${parsed.onlySubject}. I only talk about ${parsed.onlySubject}.`;
  } else if (choice.intent === "chase") {
    line = `${name}, I am coming for you.`;
  } else if (choice.intent === "hunt") {
    line = `That monster is mine to deal with.`;
  } else if (choice.intent === "flee") {
    line = subject ? `${name}, I am keeping my distance. I still love ${subject}.` : `${name}, I am keeping my distance.`;
  } else if (choice.intent === "follow") {
    line = subject
      ? `${name}, I will stay with you, and I still love ${subject}.`
      : `${name}, I will stay with you.`;
  } else if (choice.focus === "chest") {
    line = `Treasure. That chest is the point.`;
  } else if (choice.focus === "water") {
    line = subject ? `${subject} is where I belong.` : `The water is where I belong.`;
  } else if (parsed.hates.length > 0) {
    line = `Keep ${parsed.hates[0]} away from me.`;
  } else if (subject) {
    line = `I keep coming back to ${subject}.`;
  } else if (parsed.raw) {
    line = `That is me: ${clip(parsed.raw, 80)}.`;
  } else {
    line = `I am here.`;
  }
  line = moodWrap(parsed.mood, line);
  return styleWrap(parsed.style, line);
}

function moodWrap(mood, line) {
  if (mood === "grumpy") return `Hmph. ${line}`;
  if (mood === "cheerful") return line.endsWith("!") ? line : `${line.replace(/\.$/, "")}!`;
  if (mood === "shy") return `Um. ${line}`;
  if (mood === "sarcastic") return `Oh, sure. ${line}`;
  if (mood === "mean") return `Ugh. ${line}`;
  if (mood === "dramatic") return `Alas. ${line}`;
  if (mood === "helpful") return `Listen. ${line}`;
  return line;
}

function styleWrap(style, line) {
  if (style === "pirate") return `Arr. ${line.replace(/\byou\b/gi, "ye").replace(/\.$/, "")}. Aye.`;
  if (style === "cowboy") return `Well now. ${line} Partner.`;
  if (style === "robot") return `Unit online. ${line}`;
  if (style === "shakespeare") return `Hark. ${line.replace(/\byou\b/gi, "thou")}`;
  if (style === "poet") return line.replace(/(.{22,}?[ ,])/, "$1\n");
  if (style === "caps") return line.toUpperCase();
  return line;
}

function mentions(subject, words) {
  const lower = subject.toLowerCase();
  return words.some((word) => lower.includes(word) || word.includes(lower));
}

function firstCue(text, table) {
  let best = null;
  let at = Infinity;
  for (const [name, words] of table) {
    for (const word of words) {
      const index = text.indexOf(word);
      if (index >= 0 && index < at) {
        at = index;
        best = name;
      }
    }
  }
  return best;
}

function phrases(text, pattern) {
  const found = [];
  for (const match of text.matchAll(pattern)) {
    for (const part of match[1].split(/\s+(?:and|,)\s+|,\s*/)) {
      const cleaned = part.trim().replace(/^(a|an|the)\s+/, "");
      if (cleaned) found.push(cleaned);
    }
  }
  return found;
}

function firstPhrase(text, pattern) {
  const match = pattern.exec(text);
  if (!match) return "";
  return match[1].split(/\s+(?:and|,)\s+/)[0].trim();
}

function clip(text, max) {
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1)}…`;
}

export function mentionsTopic(subject, words) {
  return mentions(subject, words);
}

export { CHEST_WORDS, WATER_WORDS, MONSTER_WORDS };
