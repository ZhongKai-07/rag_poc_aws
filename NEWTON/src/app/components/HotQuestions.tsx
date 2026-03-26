import { Bot } from "lucide-react";
import { Language, getTranslation, getHotQuestions } from "../i18n/translations";
import botIcon from "figma:asset/27f58b3bbbd18d9ecbe925d4e9093be37a9712b3.png";

interface HotQuestionsProps {
  scenario: string;
  onQuestionClick: (question: string) => void;
  language: Language;
}

export function HotQuestions({ scenario, onQuestionClick, language }: HotQuestionsProps) {
  const questions = getHotQuestions(language, scenario);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <div className="w-5 h-5 flex items-center justify-center">
          <img src={botIcon} alt="AI" className="w-full h-full object-contain" />
        </div>
        <span>{getTranslation(language, "hotQuestions")}</span>
      </div>
      <div className="grid grid-cols-1 gap-2">
        {questions.map((question, index) => (
          <button
            key={index}
            onClick={() => onQuestionClick(question)}
            className="text-left px-4 py-3 bg-gray-50 hover:bg-primary/5 border border-border hover:border-primary/30 rounded-lg transition-all text-sm"
          >
            {question}
          </button>
        ))}
      </div>
    </div>
  );
}