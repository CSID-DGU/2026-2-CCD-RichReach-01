import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';

/// 추천 답변 말풍선 색
const assistantBubbleColor = Color(0xFFF3F0FF);

/// 질문 말풍선 색
const userBubbleColor = AppColors.primaryPressed;

/// 말풍선. 답변은 왼쪽(연보라), 질문은 오른쪽(진한 보라)에 꼬리가 달린다.
class ChatBubble extends StatelessWidget {
  const ChatBubble.assistant(this.text, {super.key}) : fromUser = false;

  const ChatBubble.user(this.text, {super.key}) : fromUser = true;

  final String text;
  final bool fromUser;

  static const _tailWidth = 8.0;

  @override
  Widget build(BuildContext context) {
    final color = fromUser ? userBubbleColor : assistantBubbleColor;
    final bubble = Container(
      padding: const EdgeInsets.fromLTRB(16, 13, 16, 14),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(18),
      ),
      child: WordWrapText(
        text,
        style: AppTextStyles.body.copyWith(
          fontSize: 13,
          height: 1.35,
          color: fromUser ? AppColors.onPrimary : AppColors.textPrimary,
        ),
      ),
    );
    final tail = Padding(
      padding: const EdgeInsets.only(top: 16),
      child: CustomPaint(
        size: const Size(_tailWidth, 10),
        painter: _TailPainter(color: color, pointsLeft: !fromUser),
      ),
    );

    return Align(
      alignment: fromUser ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: fromUser ? 260 : 310),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: fromUser
              ? [Flexible(child: bubble), tail]
              : [tail, Flexible(child: bubble)],
        ),
      ),
    );
  }
}

class _TailPainter extends CustomPainter {
  const _TailPainter({required this.color, required this.pointsLeft});

  final Color color;
  final bool pointsLeft;

  @override
  void paint(Canvas canvas, Size size) {
    final path = pointsLeft
        ? (Path()
            ..moveTo(size.width, 0)
            ..lineTo(0, size.height / 2)
            ..lineTo(size.width, size.height))
        : (Path()
            ..moveTo(0, 0)
            ..lineTo(size.width, size.height / 2)
            ..lineTo(0, size.height));
    canvas.drawPath(path..close(), Paint()..color = color);
  }

  @override
  bool shouldRepaint(_TailPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.pointsLeft != pointsLeft;
}

/// 빠른 질문 업종 버튼 (보라 알약).
class QuickQuestionChip extends StatelessWidget {
  const QuickQuestionChip({
    super.key,
    required this.label,
    required this.onPressed,
  });

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      button: true,
      label: '빠른 질문 $label',
      excludeSemantics: true,
      child: Material(
        color: AppColors.primary,
        shape: const StadiumBorder(),
        child: InkWell(
          onTap: onPressed,
          customBorder: const StadiumBorder(),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 9, 12, 10),
            child: Text(
              label,
              style: AppTextStyles.caption.copyWith(
                fontSize: 11,
                height: 1.1,
                fontWeight: FontWeight.w500,
                color: AppColors.onPrimary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 예시 질문 카드: 따옴표로 감싼 질문과 설명.
class ExampleQuestionCard extends StatelessWidget {
  const ExampleQuestionCard({
    super.key,
    required this.question,
    required this.description,
    required this.onPressed,
  });

  final String question;
  final String description;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(14);
    return Material(
      color: AppColors.background,
      borderRadius: radius,
      child: InkWell(
        onTap: onPressed,
        borderRadius: radius,
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          decoration: BoxDecoration(
            borderRadius: radius,
            border: Border.all(color: const Color(0xFFE6E8EF)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '“$question”',
                style: AppTextStyles.body.copyWith(
                  fontSize: 12,
                  height: 1.35,
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                description,
                style: AppTextStyles.caption.copyWith(
                  fontSize: 10,
                  height: 1.35,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 질문 입력창과 보내기 버튼. 입력 중이면 테두리가 보라로 바뀐다.
class ChatInputBar extends StatefulWidget {
  const ChatInputBar({super.key, required this.onSend, this.enabled = true});

  final ValueChanged<String> onSend;

  /// 답변을 기다리는 중이면 false (보내기 버튼이 흐려진다)
  final bool enabled;

  @override
  State<ChatInputBar> createState() => _ChatInputBarState();
}

class _ChatInputBarState extends State<ChatInputBar> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();

  /// 입력 중 테두리 — `CardRoute/Primary/70`
  static const _focusColor = Color(0xFF846BD9);

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  bool get _canSend => widget.enabled && _controller.text.trim().isNotEmpty;

  void _send() {
    if (!_canSend) return;
    widget.onSend(_controller.text);
    _controller.clear();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 50,
      padding: const EdgeInsets.only(left: 20, right: 10),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: _focusNode.hasFocus ? _focusColor : AppColors.border,
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _controller,
              focusNode: _focusNode,
              onChanged: (_) => setState(() {}),
              onSubmitted: (_) => _send(),
              textInputAction: TextInputAction.send,
              style: AppTextStyles.body.copyWith(fontSize: 13),
              decoration: InputDecoration(
                // 앱 입력칸 테마(테두리·배경)를 쓰지 않고 바깥 상자만 보여준다.
                isCollapsed: true,
                filled: false,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                contentPadding: EdgeInsets.zero,
                hintText: '가맹점과 금액을 입력해 보세요',
                hintStyle: AppTextStyles.body.copyWith(
                  fontSize: 12,
                  color: AppColors.textTertiary,
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            onPressed: _canSend ? _send : null,
            tooltip: '보내기',
            style: IconButton.styleFrom(
              backgroundColor: userBubbleColor,
              disabledBackgroundColor: AppColors.surfaceStrong,
              foregroundColor: AppColors.onPrimary,
              disabledForegroundColor: AppColors.textDisabled,
              fixedSize: const Size(30, 30),
              minimumSize: const Size(30, 30),
              padding: EdgeInsets.zero,
            ),
            icon: const Icon(Icons.arrow_upward, size: 18),
          ),
        ],
      ),
    );
  }
}
