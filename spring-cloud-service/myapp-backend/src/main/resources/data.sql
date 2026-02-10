-- ========================================
-- EASY ЗАДАЧИ (10 штук)
-- ========================================
INSERT INTO tasks (title, short_description, full_description, language_id, difficulty)
VALUES ('Palindrome Number', 'Проверить, является ли число палиндромом',
        'Дано целое число x. Вернуть true если x является палиндромом, иначе false. Число-палиндром читается одинаково слева направо и справа налево.',
        62, 'Easy'),
       ('Roman to Integer', 'Преобразовать римское число в целое',
        'Дана строка с римским числом, нужно вернуть его целочисленное значение. Используются символы I, V, X, L, C, D, M с фиксированными значениями (1, 5, 10, 50, 100, 500, 1000).',
        62, 'Easy'),
       ('Longest Common Prefix', 'Найти самый длинный общий префикс',
        'Написать функцию для поиска самого длинного общего префикса среди массива строк. Если общего префикса нет, вернуть пустую строку.',
        62, 'Easy'),
       ('Valid Parentheses', 'Проверить валидность скобок',
        'Дана строка s, содержащая только символы ''('', '')'', ''{'', ''}'', ''['' и '']''. Определить, является ли входная строка валидной.',
        62, 'Easy'),
       ('Merge Two Sorted Lists', 'Объединить два отсортированных списка',
        'Даны головы двух отсортированных связных списков list1 и list2. Объедините два списка в один отсортированный список.',
        62, 'Easy'),
       ('Remove Duplicates from Sorted Array', 'Удалить дубликаты из отсортированного массива',
        'Дан целочисленный массив nums, отсортированный по неубыванию. Удалите дубликаты in-place так, чтобы каждый уникальный элемент встречался только один раз.',
        62, 'Easy'),
       ('Remove Element', 'Удалить элемент из массива',
        'Дан целочисленный массив nums и целое число val. Удалите все вхождения val в nums in-place.', 62, 'Easy'),
       ('Find the Index of the First Occurrence', 'Найти индекс первого вхождения в строке',
        'Даны две строки needle и haystack. Вернуть индекс первого вхождения needle в haystack, или -1 если needle не является частью haystack.',
        62, 'Easy'),
       ('Search Insert Position', 'Найти позицию для вставки',
        'Дан отсортированный массив различных целых чисел и целевое значение. Вернуть индекс, если цель найдена. Если нет, вернуть индекс, куда она была бы вставлена.',
        62, 'Easy'),
       ('Length of Last Word', 'Длина последнего слова',
        'Дана строка s, состоящая из слов и пробелов. Вернуть длину последнего слова в строке.', 62, 'Easy');
-- ========================================
-- MEDIUM ЗАДАЧИ (5 штук)
-- ========================================
INSERT INTO tasks (title, short_description, full_description, language_id, difficulty)
VALUES ('Longest Balanced Subarray', 'Найти самый длинный сбалансированный подмассив',
        'Дан массив целых чисел nums. Подмассив называется сбалансированным, если количество различных чётных чисел равно количеству различных нечётных чисел. Вернуть длину самого длинного сбалансированного подмассива.',
        62, 'Medium'),

       ('Add Two Numbers', 'Сложить два числа представленных списками',
        'Даны два непустых связных списка, представляющих два неотрицательных целых числа. Цифры хранятся в обратном порядке, каждый узел содержит одну цифру. Сложить два числа и вернуть сумму как связный список.',
        62, 'Medium'),

       ('Longest Substring Without Repeating Characters',
        'Найти длину самой длинной подстроки без повторяющихся символов',
        'Дана строка s. Найти длину самой длинной подстроки без повторяющихся символов. Например, для "abcabcbb" ответ 3 ("abc").',
        62, 'Medium'),

       ('Longest Palindromic Substring', 'Найти самую длинную подстроку-палиндром',
        'Дана строка s. Вернуть самую длинную подстроку-палиндром в s. Например, для "babad" ответ "bab" или "aba".',
        62, 'Medium'),

       ('Zigzag Conversion', 'Преобразовать строку в зигзаг и прочитать построчно',
        'Строка записывается в зигзаг-паттерне на заданном количестве строк, затем читается построчно. Например, "PAYPALISHIRING" с 3 строками даёт "PAHNAPLSIIGYIR".',
        62, 'Medium');

-- ========================================
-- ТЕСТ-КЕЙСЫ ДЛЯ EASY ЗАДАЧ
-- ========================================

-- Тест-кейсы для Palindrome Number (task_id = 1)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (1, '121', 'true'),
       (1, '-121', 'false'),
       (1, '10', 'false');

-- Тест-кейсы для Roman to Integer (task_id = 2)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (2, 'III', '3'),
       (2, 'IX', '9'),
       (2, 'LVIII', '58'),
       (2, 'MCMXCIV', '1994');

-- Тест-кейсы для Longest Common Prefix (task_id = 3)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (3, 'flower\nflow\nflight', 'fl'),
       (3, 'dog\nracecar\ncar', ''),
       (3, 'interspecies\ninterstellar\ninterstate', 'inters');

-- Тест-кейсы для Valid Parentheses (task_id = 4)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (4, '()', 'true'),
       (4, '()[]{}', 'true'),
       (4, '(]', 'false'),
       (4, '([)]', 'false'),
       (4, '{[]}', 'true');

-- Тест-кейсы для Merge Two Sorted Lists (task_id = 5)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (5, '1,2,4\n1,3,4', '1,1,2,3,4,4'),
       (5, '\n', ''),
       (5, '\n0', '0');

-- Тест-кейсы для Remove Duplicates from Sorted Array (task_id = 6)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (6, '1,1,2', '2'),
       (6, '0,0,1,1,1,2,2,3,3,4', '5');

-- Тест-кейсы для Remove Element (task_id = 7)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (7, '3,2,2,3\n3', '2'),
       (7, '0,1,2,2,3,0,4,2\n2', '5');

-- Тест-кейсы для Find the Index of the First Occurrence (task_id = 8)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (8, 'sadbutsad\nsad', '0'),
       (8, 'leetcode\nleeto', '-1'),
       (8, 'hello\nll', '2');

-- Тест-кейсы для Search Insert Position (task_id = 9)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (9, '1,3,5,6\n5', '2'),
       (9, '1,3,5,6\n2', '1'),
       (9, '1,3,5,6\n7', '4');

-- Тест-кейсы для Length of Last Word (task_id = 10)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (10, 'Hello World', '5'),
       (10, '   fly me   to   the moon  ', '4'),
       (10, 'luffy is still joyboy', '6');

-- ========================================
-- ТЕСТ-КЕЙСЫ ДЛЯ MEDIUM ЗАДАЧ
-- ========================================

-- Тест-кейсы для Longest Balanced Subarray (task_id = 11)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (11, '2,5,4,3', '4'),
       (11, '3,2,2,5,4', '5'),
       (11, '1,2,3,2', '3'),
       (11, '1,2', '2');

-- Тест-кейсы для Add Two Numbers (task_id = 12)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (12, '2,4,3\n5,6,4', '7,0,8'),
       (12, '0\n0', '0'),
       (12, '9,9,9,9,9,9,9\n9,9,9,9', '8,9,9,9,0,0,0,1'),
       (12, '9,9\n1', '0,0,1');

-- Тест-кейсы для Longest Substring Without Repeating Characters (task_id = 13)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (13, 'abcabcbb', '3'),
       (13, 'bbbbb', '1'),
       (13, 'pwwkew', '3'),
       (13, 'dvdf', '3'),
       (13, 'au', '2');

-- Тест-кейсы для Longest Palindromic Substring (task_id = 14)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (14, 'babad', 'bab'),
       (14, 'cbbd', 'bb'),
       (14, 'a', 'a'),
       (14, 'racecar', 'racecar');

-- Тест-кейсы для Zigzag Conversion (task_id = 15)
INSERT INTO test_cases (task_id, input, expected_output)
VALUES (15, 'PAYPALISHIRING\n3', 'PAHNAPLSIIGYIR'),
       (15, 'PAYPALISHIRING\n4', 'PINALSIGYAHRPI'),
       (15, 'A\n1', 'A'),
       (15, 'AB\n1', 'AB');