class AppConstants {
  // App info
  static const String appName = 'Coding Academy';
  static const String appVersion = '1.0.0';

  // Language IDs (Judge0)
  static const Map<int, String> languageNames = {
    62: 'Java',
    71: 'Python',
    63: 'JavaScript',
    54: 'C++',
    50: 'C',
    51: 'C#',
    68: 'PHP',
    72: 'Ruby',
    73: 'Rust',
    74: 'TypeScript',
    60: 'Go',
    78: 'Kotlin',
    83: 'Swift',
  };

  // Difficulty levels
  static const List<String> difficultyLevels = ['Easy', 'Medium', 'Hard'];

  // Submission status
  static const String statusAccepted = 'ACCEPTED';
  static const String statusWrongAnswer = 'WRONG_ANSWER';
  static const String statusCompilationError = 'COMPILATION_ERROR';
  static const String statusRuntimeError = 'RUNTIME_ERROR';
  static const String statusTimeLimitExceeded = 'TIME_LIMIT_EXCEEDED';

  // Pagination
  static const int defaultPageSize = 20;

  // Code editor
  static const String defaultCode = '''
public class Solution {
    public static void main(String[] args) {
        // Write your code here

    }
}
''';

  static String getLanguageName(int languageId) {
    return languageNames[languageId] ?? 'Unknown';
  }

  static String getStatusDisplay(String status) {
    switch (status) {
      case statusAccepted:
        return 'Accepted ✓';
      case statusWrongAnswer:
        return 'Wrong Answer ✗';
      case statusCompilationError:
        return 'Compilation Error';
      case statusRuntimeError:
        return 'Runtime Error';
      case statusTimeLimitExceeded:
        return 'Time Limit Exceeded';
      default:
        return status;
    }
  }
}
