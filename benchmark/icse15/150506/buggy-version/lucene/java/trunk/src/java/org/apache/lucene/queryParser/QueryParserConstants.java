  - 1.3
  + 1.4
/* Generated By:JavaCC: Do not edit this line. QueryParserConstants.java */
package org.apache.lucene.queryParser;

public interface QueryParserConstants {

  int EOF = 0;
  int _NUM_CHAR = 1;
  int _ESCAPED_CHAR = 2;
  int _TERM_START_CHAR = 3;
  int _TERM_CHAR = 4;
  int _WHITESPACE = 5;
  int AND = 7;
  int OR = 8;
  int NOT = 9;
  int PLUS = 10;
  int MINUS = 11;
  int LPAREN = 12;
  int RPAREN = 13;
  int COLON = 14;
  int CARAT = 15;
  int QUOTED = 16;
  int TERM = 17;
  int FUZZY = 18;
  int SLOP = 19;
  int PREFIXTERM = 20;
  int WILDTERM = 21;
  int RANGEIN_START = 22;
  int RANGEEX_START = 23;
  int NUMBER = 24;
  int RANGEIN_TO = 25;
  int RANGEIN_END = 26;
  int RANGEIN_QUOTED = 27;
  int RANGEIN_GOOP = 28;
  int RANGEEX_TO = 29;
  int RANGEEX_END = 30;
  int RANGEEX_QUOTED = 31;
  int RANGEEX_GOOP = 32;

  int Boost = 0;
  int RangeEx = 1;
  int RangeIn = 2;
  int DEFAULT = 3;

  String[] tokenImage = {
    "<EOF>",
    "<_NUM_CHAR>",
    "<_ESCAPED_CHAR>",
    "<_TERM_START_CHAR>",
    "<_TERM_CHAR>",
    "<_WHITESPACE>",
    "<token of kind 6>",
    "<AND>",
    "<OR>",
    "<NOT>",
    "\"+\"",
    "\"-\"",
    "\"(\"",
    "\")\"",
    "\":\"",
    "\"^\"",
    "<QUOTED>",
    "<TERM>",
    "\"~\"",
    "<SLOP>",
    "<PREFIXTERM>",
    "<WILDTERM>",
    "\"[\"",
    "\"{\"",
    "<NUMBER>",
    "\"TO\"",
    "\"]\"",
    "<RANGEIN_QUOTED>",
    "<RANGEIN_GOOP>",
    "\"TO\"",
    "\"}\"",
    "<RANGEEX_QUOTED>",
    "<RANGEEX_GOOP>",
  };

}
