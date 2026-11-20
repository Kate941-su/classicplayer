---
name: plan-task
description: docs/PLAN.md のタスクを 1 つ進める（次のタスクの特定 → ブランチ → 実装 → DoD 確認 → PR）。「次のタスク」「0.3 をやって」「PLAN を進めて」と言われたときに使う。
---

# PLAN のタスクを 1 つ進める

## 1. 対象を決める
- 引数で番号（例 `0.3`）が指定されていればそれ。なければ `docs/PLAN.md` を上から見て、未完了の最初のタスク
- 未完了の判定: `gh pr list --state all --search "PLAN <番号>"` と `git log --oneline --grep "PLAN <番号>"`
- 👤（オーナー作業）のタスクは実装しない。オーナーに必要な作業と、Claude が用意できるもの（手順書・下書き）を伝える
- 依存タスク（PLAN の「※」注記）が終わっていなければ先にそれを提案する

## 2. 仕様を確認する
- `docs/SPEC.md` の該当節を読む。仕様が曖昧・未定なら推測で埋めず、SPEC に `TODO(owner): …` を書いてオーナーに質問する（AskUserQuestion）

## 3. ブランチ
- `git branch --show-current` を確認。`main` にいたら `git switch main && git pull` してから `git switch -c <type>/<番号>-<短い名前>`（例 `feat/0.3-genre-map`）

## 4. 実装とテスト
- 該当領域の `.claude/rules/*.md` に従う
- DoD の確認に必要なテストを先に書く
- 確認コマンド（CLAUDE.md の「コマンド」）をすべて通す

## 5. コミットと PR
- コミットメッセージ: `PLAN <番号>: <要約>`（日本語）
- PR は `.github/pull_request_template.md` の項目を埋める: PLAN 番号、DoD の確認結果（実行したコマンドと結果）、追加した依存のライセンスと通信の有無、UI 変更ならスクリーンショット、手動確認項目
- `gh pr create --base main`。マージはオーナーが行う
- 最後に、次のタスクの番号と内容を一言で伝える
