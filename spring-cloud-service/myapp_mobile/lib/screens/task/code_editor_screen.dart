import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_code_editor/flutter_code_editor.dart';
import 'package:flutter_highlight/themes/monokai-sublime.dart';
import 'package:highlight/languages/java.dart';
import '../../models/submission_result.dart';
import '../../models/task.dart';
import '../../providers/tasks_provider.dart';
import '../../services/task_service.dart';
import '../../utils/constants.dart';
import '../../utils/theme.dart';
import '../../widgets/loading_indicator.dart';

class CodeEditorScreen extends ConsumerStatefulWidget {
  final String taskId;

  const CodeEditorScreen({super.key, required this.taskId});

  @override
  ConsumerState<CodeEditorScreen> createState() => _CodeEditorScreenState();
}

class _CodeEditorScreenState extends ConsumerState<CodeEditorScreen> {
  late CodeController _codeController;
  bool _isSubmitting = false;
  SubmissionResult? _lastResult;

  @override
  void initState() {
    super.initState();
    _codeController = CodeController(
      text: AppConstants.defaultCode,
      language: java,
    );
  }

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  Future<void> _submitSolution(Task task) async {
    if (_codeController.text.trim().isEmpty) {
      _showSnackBar('Please write some code first', isError: true);
      return;
    }

    setState(() {
      _isSubmitting = true;
      _lastResult = null;
    });

    try {
      final taskService = ref.read(taskServiceProvider);
      final result = await taskService.submitSolution(
        task.id,
        _codeController.text,
        task.languageId,
      );

      setState(() {
        _lastResult = result;
      });

      if (result.allPassed) {
        _showSnackBar('All tests passed! ✓', isError: false);
      } else {
        _showSnackBar(
          'Some tests failed. ${result.passedTests}/${result.totalTests} passed',
          isError: true,
        );
      }
    } catch (e) {
      _showSnackBar(e.toString().replaceAll('Exception: ', ''), isError: true);
    } finally {
      setState(() {
        _isSubmitting = false;
      });
    }
  }

  void _showSnackBar(String message, {required bool isError}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? Colors.red : Colors.green,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final taskId = int.parse(widget.taskId);
    final taskAsync = ref.watch(taskProvider(taskId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Code Editor'),
        actions: [
          IconButton(
            icon: const Icon(Icons.help_outline),
            onPressed: () {
              showDialog(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('Help'),
                  content: const Text(
                    'Write your solution in the editor below and click Submit to test your code against the test cases.',
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('OK'),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
      body: taskAsync.when(
        data: (task) => _buildEditor(task),
        loading: () => const LoadingIndicator(),
        error: (error, stack) => Center(child: Text('Error: $error')),
      ),
    );
  }

  Widget _buildEditor(Task task) {
    return Column(
      children: [
        _buildTaskInfo(task),
        Expanded(
          child: SingleChildScrollView(
            child: Column(
              children: [
                _buildCodeEditor(),
                if (_lastResult != null) _buildResultCard(_lastResult!),
              ],
            ),
          ),
        ),
        _buildSubmitButton(task),
      ],
    );
  }

  Widget _buildTaskInfo(Task task) {
    return Container(
      padding: const EdgeInsets.all(16),
      color: Theme.of(context).colorScheme.surfaceVariant,
      child: Row(
        children: [
          Expanded(
            child: Text(
              task.title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
          ),
          Text(
            AppConstants.getLanguageName(task.languageId),
            style: TextStyle(
              color: Colors.grey[600],
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCodeEditor() {
    return Container(
      height: 400,
      padding: const EdgeInsets.all(8),
      child: CodeTheme(
        data: CodeThemeData(styles: monokaiSublimeTheme),
        child: SingleChildScrollView(
          child: CodeField(
            controller: _codeController,
            textStyle: const TextStyle(
              fontFamily: 'monospace',
              fontSize: 14,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildResultCard(SubmissionResult result) {
    final color = result.allPassed ? AppTheme.successGreen : AppTheme.errorRed;

    return Card(
      margin: const EdgeInsets.all(16),
      color: color.withOpacity(0.1),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  result.allPassed ? Icons.check_circle : Icons.error,
                  color: color,
                ),
                const SizedBox(width: 8),
                Text(
                  result.allPassed ? 'All Tests Passed!' : 'Some Tests Failed',
                  style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Tests: ${result.passedTests}/${result.totalTests}',
              style: const TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 8),
            Text(
              result.message,
              style: TextStyle(color: Colors.grey[700]),
            ),
            if (result.executionDetails != null) ...[
              const SizedBox(height: 12),
              const Divider(),
              const SizedBox(height: 8),
              Text(
                'Execution Details:',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  color: Colors.grey[800],
                ),
              ),
              const SizedBox(height: 8),
              Text(
                result.executionDetails!,
                style: TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 12,
                  color: Colors.grey[700],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSubmitButton(Task task) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: _isSubmitting ? null : () => _submitSolution(task),
          icon: _isSubmitting
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.send),
          label: Text(_isSubmitting ? 'Submitting...' : 'Submit Solution'),
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 16),
          ),
        ),
      ),
    );
  }
}
