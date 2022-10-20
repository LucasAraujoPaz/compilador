//@ts-check

const TOKENS =  /** @type {const}} */ ({
    IDENTIFIER: /[a-zA-Z_][a-zA-Z_\d]*/,
    BE_DEFINED_AS: /:=/,
    LEFT_PARENTHESES: /\(/,
    RIGHT_PARENTHESES: /\)/,
    LEFT_BRACKET: /\[/,
    RIGHT_BRACKET: /\]/,
    NUMBER: /\d+(\.\d+)?/,
    NUMERIC_NEGATION: /-/,
    LOGICAL_NEGATION: /!/,
    EXPONENTIATION: /\*\*/,
    MULTIPLIED_BY: /\*/,
    DIVIDED_BY: /\//,
    MODULUS: /%/,
    PLUS: /\+/,
    GREATER_THAN: />/,
    GREATER_THAN_EQUALS: />=/,
    EQUALS: /=/,
    NOT_EQUALS: /!=/,
    LESS_THAN: /</,
    LESS_THAN_EQUALS: /<=/,
    STRING: /"(?:\\"|[^"])*"/,
    COMMA: /,/,
    THIN_ARROW: /->/,
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

/** @param {string} text */
function getTokens(text) {
    
    const matcher = RegExp(
        Object.entries(TOKENS)
        .map(entry => `(?<${entry[0]}>${entry[1].source})`)
        .join('|'), "g"
    );
    
    const iterator = text.matchAll(matcher);
    
    let i = 0, line = 1;
    const getLine = (/** @type {number} */ index) => { 
        while (i < index && i < text.length) {
            if (text[i] === '\n')
                ++line;
            ++i;
        }
        return line;
    }

    /** @type {{ tokenType: string, text: string, line: number }[]} */
    const tokens = [];
    for (const i of iterator) {
        const groups = i.groups ?? (() => { throw new Error("No group"); })();
        const entries = Object.entries(groups).filter(entry => entry[1] !== undefined);
        if (entries.length === 0) 
            throw new Error("No match");
        if (entries.length > 1)
            throw new Error("More than 1 match");
        const [ tokenType, text ] = entries[0];

        if (tokenType === "WHITESPACE")
            continue;

        const token = { tokenType, text, line: getLine(i.index ?? (() => { throw new Error("No index"); })()) };
        
        if (tokenType === "ANYTHING_ELSE") 
            throw new Error(`Unexpected symbol: "${token.text}" at line ${line}`);
        
        tokens.push(token);
    }

    return tokens;
}
