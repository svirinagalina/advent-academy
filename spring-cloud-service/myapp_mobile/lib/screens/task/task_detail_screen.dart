import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../models/task.dart';
import '../../providers/tasks_provider.dart';
import '../../providers/submissions_provider.dart';
import '../../widgets/loading_indicator.dart';
import '../../widgets/difficulty_badge.dart';
import '../../utils/constants.dart';

class TaskDetailScreen extends ConsumerStatefulWidget {
  final String taskId;

  const TaskDetailScreen({super.key, required this.taskId});

  @override
  ConsumerState<TaskDetailScreen> createState() => _TaskDetailScreenState();
}

class _TaskDetailScreenState extends ConsumerState<TaskDetailScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final taskId = int.parse(widget.taskId);
    final taskAsync = ref.watch(taskProvider(taskId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Task Details'),
      ),
      body: taskAsync.when(
        data: (task) => _buildContent(task),
        loading: () => const LoadingIndicator(message: 'Loading task...'),
        error: (error, stack) => _buildError(error.toString()),
      ),
    );
  }

  Widget _buildContent(Task task) {
    return Column(
      children: [
        _buildHeader(task),
        TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Description'),
            Tab(text: 'Submissions'),
            Tab(text: 'Statistics'),
          ],
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _buildDescription(task),
              _buildSubmissions(task.id),
              _buildStatistics(task.id),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildHeader(Task task) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  task.title,
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ),
              const SizedBox(width: 8),
              DifficultyBadge(difficulty: task.difficulty),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.code, size: 16, color: Colors.grey[600]),
              const SizedBox(width: 4),
              Text(
                AppConstants.getLanguageName(task.languageId),
                style: TextStyle(color: Colors.grey[600]),
              ),
            ],
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: () => context.push('/task/${task.id}/editor'),
              icon: const Icon(Icons.code),
              label: const Text('Start Coding'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDescription(Task task) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Description',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: 16),
          Text(
            task.fullDescription,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ],
      ),
    );
  }

  Widget _buildSubmissions(int taskId) {
    final submissionsAsync = ref.watch(taskSubmissionsProvider(taskId));

    return submissionsAsync.when(
      data: (paginated) {
        if (paginated.content.isEmpty) {
          return const Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.inbox_outlined, size: 64, color: Colors.grey),
                SizedBox(height: 16),
                Text('No submissions yet'),
                SizedBox(height: 8),
                Text(
                  'Start coding to see your submissions here',
                  style: TextStyle(color: Colors.grey),
                ),
              ],
            ),
          );
        }

        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: paginated.content.length,
          itemBuilder: (context, index) {
            final submission = paginated.content[index];
            return Card(
              margin: const EdgeInsets.only(bottom: 12),
              child: ListTile(
                leading: Icon(
                  submission.status == 'ACCEPTED'
                      ? Icons.check_circle
                      : Icons.error,
                  color: submission.status == 'ACCEPTED'
                      ? Colors.green
                      : Colors.red,
                ),
                title: Text(AppConstants.getStatusDisplay(submission.status)),
                subtitle: Text(
                  '${submission.passedTests ?? 0}/${submission.totalTests ?? 0} tests passed',
                ),
                trailing: Text(
                  _formatDate(submission.createdAt),
                  style: const TextStyle(fontSize: 12),
                ),
              ),
            );
          },
        );
      },
      loading: () => const LoadingIndicator(),
      error: (error, stack) => Center(
        child: Text('Error: ${error.toString()}'),
      ),
    );
  }

  Widget _buildStatistics(int taskId) {
    final statsAsync = ref.watch(taskStatisticsProvider(taskId));

    return statsAsync.when(
      data: (stats) {
        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              _buildStatCard(
                'Total Attempts',
                stats.totalAttempts.toString(),
                Icons.send,
              ),
              const SizedBox(height: 12),
              _buildStatCard(
                'Successful Attempts',
                stats.successfulAttempts.toString(),
                Icons.check_circle,
                color: Colors.green,
              ),
              const SizedBox(height: 12),
              _buildStatCard(
                'Status',
                stats.solved ? 'Solved ✓' : 'Not Solved',
                Icons.emoji_events,
                color: stats.solved ? Colors.amber : Colors.grey,
              ),
            ],
          ),
        );
      },
      loading: () => const LoadingIndicator(),
      error: (error, stack) => Center(
        child: Text('Error: ${error.toString()}'),
      ),
    );
  }

  Widget _buildStatCard(String title, String value, IconData icon,
      {Color? color}) {
    return Card(
      child: ListTile(
        leading: Icon(icon, color: color ?? Theme.of(context).colorScheme.primary),
        title: Text(title),
        trailing: Text(
          value,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.bold,
                color: color,
              ),
        ),
      ),
    );
  }

  Widget _buildError(String error) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 64, color: Colors.red),
          const SizedBox(height: 16),
          Text('Error: $error'),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: () {
              ref.invalidate(taskProvider(int.parse(widget.taskId)));
            },
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime date) {
    return '${date.day}/${date.month}/${date.year}';
  }
}
