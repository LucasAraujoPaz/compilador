//@ts-check

const TOKENS =  /** @type {const}} */ ({
    NUMBER: /\d+(\.\d+)?/,
    STRING: /"(?:\\"|[^"])*"/,
    IDENTIFIER: /[a-zA-Z_][a-zA-Z_\d]*/,

    BE_DEFINED_AS: /:=/,
    GREATER_THAN_EQUALS: />=/,
    LESS_THAN_EQUALS: /<=/,
    NOT_EQUALS: /!=/,
    EXPONENTIATION: /\*\*/,
    THIN_ARROW: /->/,

    LEFT_PARENTHESIS: /\(/,
    RIGHT_PARENTHESIS: /\)/,
    LEFT_BRACKET: /\[/,
    RIGHT_BRACKET: /\]/,
    NUMERIC_NEGATION: /-/,
    LOGICAL_NEGATION: /!/,
    MULTIPLIED_BY: /\*/,
    DIVIDED_BY: /\//,
    MODULUS: /%/,
    PLUS: /\+/,
    GREATER_THAN: />/,
    LESS_THAN: /</,
    EQUALS: /=/,
    COMMA: /,/,
    COLON: /:/,
    DOT: /\./,
    WHITESPACE: /\s+/,
    ANYTHING_ELSE: /\S+/
});

const KEYWORDS = /** @type {const} */ ({
    NUMBER_TYPE: "Number",
    BOOLEAN_TYPE: "Boolean",
    STRING_TYPE: "String",
    ARRAY_TYPE: "Array",
    FUNCTION_TYPE: "Function",
    ANY_TYPE: "Any",
    LET: "Let",
    TRUE: "True",
    FALSE: "False",
    AND: "And",
    OR: "Or",
    IF: "If",
    THEN: "Then",
    ELSE: "Else",
    END: "End",
});

const matcher = RegExp(Object.entries(TOKENS)
    .map(value => `(?<${value[0]}>${value[1].source})`)
    .join('|'), "g"
);

function lineGetter (/** @type {string} */ text) {
    
    let i = 0, line = 1;
    
    return function getLine(/** @type {number} */ index) { 
        while (i < Math.min(index, text.length)) {
            if (text[i] === '\n')
                ++line;
            ++i;
        }
        return line;
    }
}

/** @type {{[s in KEYWORDS[keyof KEYWORDS]]: keyof KEYWORDS }} */ 
//@ts-expect-error
const keywordsToTypes = Object.fromEntries(
    Object.entries(KEYWORDS).map(entry => [entry[1], entry[0]])
);

/** @type {(lexeme: string) => keyof KEYWORDS | "IDENTIFIER"} */
function resolveIdentifier(/** @type {string} */ lexeme) {
    return keywordsToTypes[lexeme] ?? "IDENTIFIER";
};

/** 
 * @typedef {{
 * tokenType: keyof Omit<TOKENS, "WHITESPACE" | "ANYTHING_ELSE"> | keyof KEYWORDS, 
 * lexeme: string, 
 * line: number
 * }} Token 
 * */

/** 
 * @param {string} text 
 * @returns {Token[]}
*/
function getTokens(text) {
    const iterator = text.matchAll(matcher);
    
    const getLine = lineGetter(text);

    /** @type {Token[]} */
    const tokens = [];
    for (const i of iterator) {
        const groups = i.groups ?? (() => { throw new Error("No group") })();
        const entries = Object.entries(groups).filter(e => e[1] !== undefined);
        if (entries.length === 0) 
            throw new Error("No match");
        if (entries.length > 1)
            throw new Error("More than 1 match");
        
        const [ tokenType, lexeme ] = entries[0];

        if (tokenType === "WHITESPACE")
            continue;
        
        const token = { 
            tokenType: /** @type {Token["tokenType"]} */ (tokenType === "IDENTIFIER" ? resolveIdentifier(lexeme) : tokenType), 
            lexeme,
            line: getLine(i.index ?? (() => { throw new Error("No index") })())};
        
        if (tokenType === "ANYTHING_ELSE") 
            throw new Error(`Unexpected symbol: "${token.text}" at line ${token.line}`);
        
        tokens.push(token);
    }

    return tokens;
}

const text = 
`Let factorial := 
    Function(Number x) -> Number:
        If x < 2 Then
            1
        Else
            x * factorial(x - 1)
        End
    End
.`;

getTokens(text);