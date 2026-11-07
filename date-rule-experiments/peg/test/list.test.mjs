import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import peggy from 'peggy';

const grammar = readFileSync(new URL('../list.pegjs', import.meta.url), 'utf8');
const parser = peggy.generate(grammar);

test('(start:end) expands with step 1', () => {
  assert.deepEqual(parser.parse('(3:7)'), [3, 4, 5, 6, 7]);
});

test('(start:step:end) expands with the given step', () => {
  assert.deepEqual(parser.parse('(3:5:14)'), [3, 8, 13]);
});

test('whitespace around numbers is ignored', () => {
  assert.deepEqual(parser.parse('( 2 : 7 : 23 )'), [2, 9, 16, 23]);
});

test('start greater than end yields an empty list', () => {
  assert.deepEqual(parser.parse('(9:1)'), []);
});

test('malformed input is rejected', () => {
  assert.throws(() => parser.parse('(3:)'), { name: 'SyntaxError' });
  assert.throws(() => parser.parse('3:5'), { name: 'SyntaxError' });
});
