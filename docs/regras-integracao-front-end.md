## Regras de Integração com o Front-End

### 1. Cumprimento do contrato

- Toda implementação que exponha ou altere endpoints REST, mensagens STOMP, destinos, eventos, DTOs ou formatos de resposta deve seguir o [Contrato de Integração com o Front-End](docs/contrato-integracao.md).
- O contrato de integração é a referência compartilhada entre as equipes para os detalhes de comunicação. O código e os testes devem ser verificados para confirmar o comportamento efetivamente implementado.
- Não alterar unilateralmente nomes de eventos, destinos, campos, códigos de erro, métodos HTTP, rotas ou estruturas de payload.

### 2. Atualização do contrato

- Toda PR que criar, modificar ou remover um endpoint REST, destino STOMP, evento, DTO ou comportamento observável pelo frontend deve atualizar o contrato de integração na mesma PR.
- Registrar o estado real da implementação, exemplos de requisição e resposta, erros relevantes e eventuais divergências.
- Não documentar funcionalidades planejadas como se já estivessem implementadas.

### 3. Compatibilidade e comunicação

- Mudanças incompatíveis com o contrato vigente devem ser identificadas como `BREAKING`, justificadas e comunicadas ao time de frontend antes do merge.
- Mudanças de contrato devem ser acordadas entre backend e frontend, incluindo eventuais alterações necessárias no cliente.
- Quando houver divergência entre o contrato, o código, os testes e o Swagger, registrar e resolver a divergência antes de considerar a integração concluída.

### 4. Validação da integração

- Validar os critérios de aceite específicos do card e executar os testes pertinentes.
- Confirmar que os nomes, campos, destinos, autenticação, códigos de erro e formatos definidos no contrato correspondem à implementação.
- O revisor deve conferir a atualização do contrato quando aplicável e bloquear o merge caso a mudança exigida não esteja documentada.

### 5. Responsabilidade

- O responsável pela implementação deve atualizar o contrato e comunicar mudanças incompatíveis.
- O revisor da PR deve verificar a conformidade com o contrato e os critérios de aceite.
- Dúvidas ou decisões não definidas devem ser alinhadas com o time de frontend antes de implementar um comportamento incompatível.

### 6. Padronização e Sincronização do Catálogo de Erros

- **Padrão único de erros:** O catálogo de erros definido em `docs/contrato-integracao.md` e a seção **“Formato de Erro”** do `README.md` devem seguir o mesmo padrão e estrutura, mantendo consistência entre códigos, mensagens, formatos de resposta e demais informações documentadas.
- **Sincronização obrigatória:** Qualquer inclusão, alteração ou remoção de código de erro, mensagem, estrutura, campo ou comportamento relacionado a erros deve ser refletida **em ambos os documentos na mesma PR**.
- **Erros REST/HTTP e STOMP:** As mesmas regras de padronização e sincronização se aplicam aos erros retornados pelos endpoints REST/HTTP e aos erros enviados por meio de mensagens STOMP. Os formatos específicos de cada mecanismo devem respeitar o contrato de integração acordado com o Front-End.
- **Consistência com a implementação:** A documentação, o comportamento implementado e os testes devem estar alinhados. Não devem existir códigos, mensagens ou estruturas de erro implementados que contrariem o padrão documentado.
- **Responsabilidade na PR:** A pessoa responsável pela alteração deve atualizar os dois documentos e os testes pertinentes. A revisão da PR deve verificar a consistência entre o `README.md`, o `docs/contrato-integracao.md` e a implementação antes da aprovação.